package com.alignedcardio.itsm;

import com.microsoft.applicationinsights.attach.ApplicationInsights;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ItsPortalApplication {

    public static void main(String[] args) {
        ApplicationInsights.attach();
        SpringApplication.run(ItsPortalApplication.class, args);
    }
}
