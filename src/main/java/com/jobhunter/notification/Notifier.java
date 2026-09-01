package com.jobhunter.notification;

import com.jobhunter.model.Job;
import java.util.List;

public interface Notifier {

    boolean notify(List<Job> jobs);

    default void notifyStatus(String message) {}
}
