package com.example;

import com.example.cli.CliTool;
import picocli.CommandLine;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        int exitCode = new CommandLine(new CliTool()).execute(args);
        System.exit(exitCode);
    }
}
