package com.running.api;

import com.running.api.util.TrainingTimeUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class RunningApiApplication {

    public static void main(String[] args) {
        // LocalDateTime.now() y los cron de Spring usan la hora del servidor: fijamos la zona
        // del atleta para que la API no dependa del TZ del contenedor/host.
        TimeZone.setDefault(TimeZone.getTimeZone(TrainingTimeUtils.DEFAULT_TIMEZONE));
        SpringApplication.run(RunningApiApplication.class, args);
    }
}
