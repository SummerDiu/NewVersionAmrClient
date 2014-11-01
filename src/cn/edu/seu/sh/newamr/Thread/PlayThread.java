package cn.edu.seu.sh.newamr.Thread;

import android.media.MediaPlayer;
import android.os.Environment;
import android.util.Log;

import java.io.File;


public class PlayThread extends Thread {
    private boolean keepRunning = true;
    private MediaPlayer mediaPlayer = null;
    @Override
    public void run() {
        mediaPlayer = new MediaPlayer();
        while(keepRunning){

        }
    }

    public void stopThread(){
        keepRunning = false;
    }

    public void playFile(int fileIndex){
        String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        String filePath = dirPath + "/cacheFile"+fileIndex+".amr";
        //File file = new File(filePath);
        Log.d("playFile func", "try to play file " + filePath);
        try {
                mediaPlayer.reset();
                mediaPlayer.setDataSource(filePath);
                mediaPlayer.prepare();
                mediaPlayer.start();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
