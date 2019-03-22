package sample.objects.tasks;

import java.util.Timer;
import java.util.TimerTask;

public class UpdateConnectionStatusTimer {
    private Timer timer = new Timer();
    private TimerTask timerTask;

    public void start(TimerTask timerTask, long delay, long period){
        this.timerTask = timerTask;
        timer.scheduleAtFixedRate(this.timerTask, delay, period);
    }

    public void stop(){
        timerTask.cancel();
        timer.purge();
    }
}
