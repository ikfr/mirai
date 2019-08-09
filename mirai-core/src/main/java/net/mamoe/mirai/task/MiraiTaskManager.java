package net.mamoe.mirai.task;


import lombok.Getter;
import net.mamoe.mirai.MiraiServer;
import net.mamoe.mirai.event.events.server.ServerDisableEvent;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class MiraiTaskManager {

    private static MiraiTaskManager instance;

    public static MiraiTaskManager getInstance(){
        if(MiraiTaskManager.instance == null){
            MiraiTaskManager.instance = new MiraiTaskManager();
        }
        return MiraiTaskManager.instance;
    }

    private MiraiThreadPool pool;

    private MiraiTaskManager() {
        this.pool = new MiraiThreadPool();
        MiraiServer.getInstance().getEventManager()
                .onEvent(ServerDisableEvent.class)
                .setHandler(a -> this.pool.close());
    }

    /**
    基础Future处理
     */

    public void execute(Runnable runnable){
        this.execute(runnable,MiralTaskExceptionHandler.byDefault());
    }

    public void execute(Runnable runnable, MiralTaskExceptionHandler handler){
        this.pool.execute(() ->
        {
            try{
                runnable.run();
            }catch (Exception e){
                handler.onHandle(e);
            }
        });
    }


    public <D> Future<D> submit(Callable<D> callable) {
        return this.submit(callable, MiralTaskExceptionHandler.byDefault());
    }

    public <D> Future<D> submit(Callable<D> callable, MiralTaskExceptionHandler handler) {
        return this.pool.submit(() -> {
            try {
                return callable.call();
            } catch (Throwable e) {
                handler.onHandle(e);
                return null;
            }
        });
    }

    /**
     异步任务
     */
    public <D> void ansycTask(Callable<D> callable, Consumer<D> callback){
        this.ansycTask(callable,callback,MiralTaskExceptionHandler.byDefault());
    }

    public <D> void ansycTask(Callable<D> callable, Consumer<D> callback,  MiralTaskExceptionHandler handler){
        this.pool.execute(() -> {
            try {
                callback.accept(callable.call());
            } catch (Throwable e) {
                handler.onHandle(e);
            }
        });
    }

    /**
     定时任务
     */

    public void repeatingTask(Runnable runnable, long interval){
        this.repeatingTask(runnable,interval, MiralTaskExceptionHandler.byDefault());
    }

    public void repeatingTask(Runnable runnable, long interval,  MiralTaskExceptionHandler handler){
        this.repeatingTask(runnable,interval,a -> true,handler);
    }

    public void repeatingTask(Runnable runnable, long interval, int times){
        this.repeatingTask(runnable,interval,times,MiralTaskExceptionHandler.byDefault());
    }

    public void repeatingTask(Runnable runnable, long interval, int times,  MiralTaskExceptionHandler handler){
        AtomicInteger integer = new AtomicInteger(times);
        this.repeatingTask(
                runnable,interval, a ->  integer.getAndDecrement() > 0, handler
        );
    }

    public <D extends Runnable> void repeatingTask(D runnable, long interval, Predicate<D> shouldContinue, MiralTaskExceptionHandler handler){
        new Thread(() -> {
            do {
                this.pool.execute(() -> {
                    try {
                        runnable.run();
                    } catch (Exception e) {
                        handler.onHandle(e);
                    }
                });
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (shouldContinue.test(runnable));
        }).start();
    }

    public void deleteTask(Runnable runnable, long interval){
        new Thread(() -> {
            try{
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            this.pool.execute(runnable);
        }).start();
    }

}
