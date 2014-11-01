package cn.edu.seu.sh.newamr.Thread;

import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.app.Activity;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;
import android.widget.Toast;
import cn.edu.seu.sh.newamr.Config.CommonConfig;
import cn.edu.seu.sh.newamr.User.Client;

public class SendThread {
    private static final String TAG = "ArmAudioEncoder";
    private static SendThread amrAudioEncoder = null;
    private Activity activity;
    private MediaRecorder audioRecorder;
    private boolean isAudioRecording;
    private LocalServerSocket lss;
    private LocalSocket sender;
	private LocalSocket receiver;
    private DataInputStream dataInput;
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式    

    private SendThread() {    }

    public static SendThread getArmAudioEncoderInstance() {
        if (amrAudioEncoder == null) {
            synchronized (SendThread.class) {
                if (amrAudioEncoder == null) {
                    amrAudioEncoder = new SendThread();
                }
            }
        }
        return amrAudioEncoder;
    }

    public void initArmAudioEncoder(Activity activity) {
        this.activity = activity;
        isAudioRecording = false;
    }

    public void start() {
    	this.isAudioRecording = true;
       initLocalSocket();
       initAudioRecorder();
       new Thread(new AudioCaptureThread()).start();
    }

    private boolean initLocalSocket() {
        boolean ret = true;
        try {
            releaseLocalSocket();
            String serverName = "armAudioServer";
            final int bufSize = 64;
            lss = new LocalServerSocket(serverName);
            receiver = new LocalSocket();
            receiver.connect(new LocalSocketAddress(serverName));
            receiver.setReceiveBufferSize(bufSize);
            receiver.setSendBufferSize(bufSize);
            sender = lss.accept();
            sender.setReceiveBufferSize(bufSize);
            sender.setSendBufferSize(bufSize);
        } catch (IOException e) {
            ret = false;
        }
        return ret;
    }

    private boolean initAudioRecorder() {
        if (audioRecorder != null) {
            audioRecorder.reset();
            audioRecorder.release();
        }
        audioRecorder = new MediaRecorder();
        audioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        audioRecorder.setOutputFormat(MediaRecorder.OutputFormat.RAW_AMR);
        final int mono = 1;
        audioRecorder.setAudioChannels(mono);
        audioRecorder.setAudioSamplingRate(8000);
        audioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        audioRecorder.setOutputFile(sender.getFileDescriptor());

        boolean ret = true;
        try {
            audioRecorder.prepare();
            audioRecorder.start();
        } catch (Exception e) {
            releaseMediaRecorder();
            showToastText("手机不支持录音此功能");
            ret = false;
        }
        return ret;
    }

    private boolean isAudioRecording() {
		return isAudioRecording;
	}
    
    public void stop() {
        if (isAudioRecording) {
            isAudioRecording = false;
        }
        releaseAll();
    }

    private void releaseAll() {
        releaseMediaRecorder();
        releaseLocalSocket();
        amrAudioEncoder = null;
    }

    private void releaseMediaRecorder() {
        try {
            if (audioRecorder == null) {
                return;
            }
            if (isAudioRecording) {
                audioRecorder.stop();
                isAudioRecording = false;
            }
            audioRecorder.reset();
            audioRecorder.release();
            audioRecorder = null;
        } catch (Exception err) {
            Log.d(TAG, err.toString());
            printInfoToFileWithTime("releaseMediaRecorder error:"+err.toString());
        }
    }

    private void releaseLocalSocket() {
        try {
            if (sender != null) {
                sender.close();
            }
            if (receiver != null) {
                receiver.close();
            }
            if (lss != null) {
                lss.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            printInfoToFileWithTime("releaseLocalSocket error:"+e.toString());
        }
        sender = null;
        receiver = null;
        lss = null;
    }
    
    class AudioCaptureThread implements Runnable{
    	@Override
    	public void run() {
    		try {
    			dataInput = new DataInputStream(receiver.getInputStream());
                sendAmrAudio();
            } catch (Exception e) {
                Log.e(TAG, "sendAmrAudio() 出错");
            }
        }
    	
    	private void sendAmrAudio() throws Exception {
            DatagramSocket udpSocket = new DatagramSocket();
            skipAmrHead(dataInput);
            final int SEND_FRAME_COUNT_ONE_TIME = 1;// 每次发送10帧的数据，1帧大约32B
            // AMR格式：http://blog.csdn.net/dinggo/article/details/1966444
            final int BLOCK_SIZE[] = { 12, 13, 15, 17, 19, 20, 26, 31, 5, 0, 0, 0, 0, 0, 0, 0 };
            byte[] sendBuffer = new byte[64];
            while (isAudioRecording()) {
                int offset = 0;
                for (int index = 0; index < SEND_FRAME_COUNT_ONE_TIME; ++index) {
                    if (!isAudioRecording()) {
                        break;
                    }
                    dataInput.read(sendBuffer, offset, 1);
                    int blockIndex = (int) (sendBuffer[offset] >> 3) & 0x0F;
                    int frameLength = BLOCK_SIZE[blockIndex];
                    readSomeData(sendBuffer, offset + 1, frameLength, dataInput);
                    offset += frameLength + 1;
                }
                udpSend(udpSocket, sendBuffer, offset);

            }
            udpSocket.close();
            dataInput.close();
            releaseAll();
        }

        private void skipAmrHead(DataInputStream dataInput) {
            final byte[] AMR_HEAD = new byte[] { 0x23, 0x21, 0x41, 0x4D, 0x52, 0x0A };
            int result = -1;
            int state = 0;
            try {
                while (-1 != (result = dataInput.readByte())) {
                    if (AMR_HEAD[0] == result) {
                        state = (0 == state) ? 1 : 0;
                    } else if (AMR_HEAD[1] == result) {
                        state = (1 == state) ? 2 : 0;
                    } else if (AMR_HEAD[2] == result) {
                        state = (2 == state) ? 3 : 0;
                    } else if (AMR_HEAD[3] == result) {
                        state = (3 == state) ? 4 : 0;
                    } else if (AMR_HEAD[4] == result) {
                        state = (4 == state) ? 5 : 0;
                    } else if (AMR_HEAD[5] == result) {
                        state = (5 == state) ? 6 : 0;
                    }

                    if (6 == state) {
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "read mdat error...");
            }
        }

        private void readSomeData(byte[] buffer, int offset, int length, DataInputStream dataInput) {
            int numOfRead = -1;
            while (true) {
                try {
                    numOfRead = dataInput.read(buffer, offset, length);
                    if (numOfRead == -1) {
                        Thread.sleep(100);
                    } else {
                        offset += numOfRead;
                        length -= numOfRead;
                        if (length <= 0) {
                            break;
                        }
                    }
                } catch (Exception e) {
                	e.printStackTrace();
                    break;
                }
                printInfoToFileWithTime("readSomeData runs..");
            }
        }

        private void udpSend(DatagramSocket udpSocket, byte[] buffer, int sendLength) {
            try {
                InetAddress ip = InetAddress.getByName(CommonConfig.CLIENT_A_IP_ADDRESS.trim());//换目标通信对象时需要改这里的CLIENT_A_IP_ADDRESS;;
                int port = CommonConfig.CLIENT_A_PORT;//这里指定对方的接收端口;AUDIO_SERVER_UP_PORT

                byte[] sendBuffer = new byte[sendLength];
                System.arraycopy(buffer, 0, sendBuffer, 0, sendLength);

                DatagramPacket packet = new DatagramPacket(sendBuffer, sendLength);
                packet.setAddress(ip);
                packet.setPort(port);
                udpSocket.send(packet);
                System.out.println("to"+packet.getAddress()+"length:"+packet.getLength());
               
            } catch (IOException e) {
                e.printStackTrace();
                printInfoToFileWithTime("udpSend error:"+e.toString());
            }
        }

    }

    private void showToastText(String msg) {
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
    }
    
    public  void printInfoToFile(String s)
    {
        try {
            FileOutputStream  fos = new FileOutputStream ("/sdcard/AmrAudioEncoder.txt",true);

            byte bytes[] = new byte[2048];
            bytes = s.getBytes();
            int b = s.length();
            fos.write(bytes, 0, b);
            fos.close();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
    public void printInfoToFileWithTime(String s )
    {
        System.out.println(df.format(new Date())+":"+ s+"\r\n\r\n");
    }

}
