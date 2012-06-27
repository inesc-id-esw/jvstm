package jvstm.scheduler;

public class DefaultSchedulerFactory implements SchedulerFactory{

    public Scheduler makeSerializerScheduler() {
	Scheduler.setScheduler(new SerializerScheduler());
	return Scheduler.getScheduler();
    }
    
    public Scheduler makeWaitScheduler() {
	Scheduler.setScheduler(new WaitScheduler());
	return Scheduler.getScheduler();
    }
    
    public Scheduler makeWaitRelaxedScheduler() {
	Scheduler.setScheduler(new WaitRelaxedScheduler());
	return Scheduler.getScheduler();
    }
    
}
