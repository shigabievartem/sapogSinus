package sample.objects.tasks;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class UpdateConnectionStatusScheduler {
    // Сервис обработки
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    // Выполняемая операция
    private ScheduledFuture scheduledFuture;

    public void start(Runnable task, long delay, long period) {
        scheduledFuture = executorService.scheduleAtFixedRate(task, delay, period, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduledFuture.cancel(true);
    }

    public void stopScheduler() {
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
