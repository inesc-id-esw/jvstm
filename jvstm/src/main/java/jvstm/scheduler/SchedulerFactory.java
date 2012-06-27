package jvstm.scheduler;

public interface SchedulerFactory {

    public Scheduler makeSerializerScheduler();
    
    public Scheduler makeWaitScheduler();
    
    public Scheduler makeWaitRelaxedScheduler();
    
}
