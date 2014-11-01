package cn.edu.seu.sh.newamr.Thread;

import android.os.Environment;

import cn.edu.seu.sh.newamr.Config.CommonConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Observable;


public class ReceiveThread extends Thread {

    private boolean keepRunning = true;
    private PlayThread playThread = null;
    private DatagramSocket socket = null;
    private ArrayList<File> fileList = null;
    private int fileIndex = 0;

    public ReceiveThread(PlayThread playThread){
        this.playThread = playThread;
    }

    @Override
    public void run() {
        initSocket();
        initFileList();
        while(keepRunning){
            receiveData();
            playThread.playFile(fileIndex);
            fileIndex=++fileIndex%5;
        }
    }

    public void stopThread(){
        keepRunning = false;
    }

    private void initSocket(){
        if(socket==null){
            try{
                socket = new DatagramSocket(CommonConfig.CLIENT_A_PORT);//定义接收端口,不变
            }catch(Exception e){
                e.printStackTrace();
            }
        }

    }

    private void initFileList(){
        fileList = new ArrayList<File>();
        String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        try{
            for(fileIndex = 0;fileIndex<5;fileIndex++)
            {
                String filePath = dirPath + "/cacheFile"+fileIndex+".amr";
                File file = new File(filePath);
                fileList.add(file);
            }
            fileIndex=0;
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void receiveData(){
        final int BUFFER_SIZE = 256;// 10kb buffer10*1024
        // size:1024******20140721修改
        final int RECEIVE_FRAME_COUNT_TIME = 7;// //10******20140721修改20140917
        byte[] ds = new byte[BUFFER_SIZE];
        byte[] packetBuffer = new byte[32];// ////////////1024

        DatagramPacket packet = new DatagramPacket(packetBuffer,
                packetBuffer.length);

        try {
            FileOutputStream outputStream = new FileOutputStream(fileList.get(fileIndex));
            int offset = 0;
            for(int i = 0;i<RECEIVE_FRAME_COUNT_TIME;i++){
                socket.receive(packet);
                System.out.println("from"+packet.getAddress()+"length:"+packet.getLength());
                System.arraycopy(packet.getData(), 0, ds, offset,
                        packet.getLength());
                offset += packet.getLength();
            }
            final byte[] AMR_HEAD = new byte[] { 0x23, 0x21, 0x41, 0x4D, 0x52,
                    0x0A };
           outputStream.write(AMR_HEAD, 0, AMR_HEAD.length);
           outputStream.write(ds, 0, offset);
           outputStream.flush();
           outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}