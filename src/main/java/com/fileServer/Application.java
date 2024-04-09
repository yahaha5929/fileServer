package com.fileServer;

import com.fileServer.nettyServer.FileServerBoot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application implements CommandLineRunner {

    @Autowired
    private FileServerBoot fileServerBoot;

    public static void main(String args[]) {
        SpringApplication.run(Application.class);
    }

    public void run(String... args) throws Exception {
        fileServerBoot.start();
    }
}
