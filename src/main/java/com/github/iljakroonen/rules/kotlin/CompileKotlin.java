package com.github.iljakroonen.rules.kotlin;

import com.google.devtools.build.buildjar.jarhelper.JarCreator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class CompileKotlin {
    public static void main(String[] args) {
        try {
            String kotlincExecutablePath = args[0];
            String jdkHome = args[1];
            String[] kotlinSources = args[2].split(System.getProperty("path.separator"));
            String compileClasspath = args[3];
            Path outputJar = Paths.get(args[4]);
            String moduleName = args[5];
            Path outputSourceJar = Paths.get(args[6]);
            String[] resources = args[7].split(System.getProperty("path.separator"));
            String kotlinHome = args[8];

            Path tempCompilationDirectory = Files.createTempDirectory("kotlinc_outputs");

            String[] kotlincFlags = new String[]{
                    "-d",
                    tempCompilationDirectory.toString(),
                    "-cp",
                    compileClasspath,
                    "-jdk-home",
                    jdkHome,
                    "-module-name",
                    moduleName,
                    "-jvm-target",
                    "1.8",
                    "-kotlin-home",
                    kotlinHome,
                    "-language-version",
                    "1.2"
            };

            ArrayList<String> kotlincArgs = new ArrayList<>();
            kotlincArgs.add(kotlincExecutablePath);
            kotlincArgs.addAll(Arrays.asList(kotlinSources));
            kotlincArgs.addAll(Arrays.asList(kotlincFlags));

            Process process = new ProcessBuilder()
                    .command(kotlincArgs)
                    .redirectError(ProcessBuilder.Redirect.INHERIT).redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .start();

            int outCode = process.waitFor();

            if (outCode != 0)
                System.exit(outCode);

            JarCreator jarCreator = new JarCreator(outputJar);
            jarCreator.addDirectory(tempCompilationDirectory);

            List<String> sortedResources = Arrays.asList(resources);
            Collections.sort(sortedResources);
            for (String resource : sortedResources) {
                Path resourcePath = Paths.get(resource);
                boolean foundSrc = false;
                boolean foundResources = false;
                ArrayList<String> targetLocationPathParts = new ArrayList<>();

                for (Path pathPart : resourcePath) {
                    if (foundResources) {
                        targetLocationPathParts.add(pathPart.toString());
                    }

                    if (pathPart.toString().equals("src")) {
                        foundSrc = true;
                    }

                    if (foundSrc && pathPart.toString().equals("resources")) {
                        foundResources = true;
                    }
                }

                String destinationInJar = String.join("/", targetLocationPathParts);

                jarCreator.addEntry(destinationInJar, resourcePath);
            }

            jarCreator.setCompression(true);
            jarCreator.setNormalize(true);
            jarCreator.execute();

            JarCreator sourceJarCreator = new JarCreator(outputSourceJar);
            sourceJarCreator.setCompression(true);
            sourceJarCreator.setNormalize(true);
            List<String> kotlinSourcesSorted = Arrays.asList(kotlinSources);
            Collections.sort(kotlinSourcesSorted);

            for (String source : kotlinSourcesSorted) {
                Path sourcePath = Paths.get(source);
                String sourceContents = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
                String packageName = null;

                for (String line : sourceContents.split("\n")) {
                    if (line.startsWith("package ")) {
                        String withPossibleTrailingSemicolon = line.substring("package ".length()).trim();
                        if (withPossibleTrailingSemicolon.endsWith(";"))
                            packageName = withPossibleTrailingSemicolon.substring(0, withPossibleTrailingSemicolon.length() - 1).trim();
                        else
                            packageName = withPossibleTrailingSemicolon;

                        break;
                    }
                }

                if (packageName == null) {
                    System.err.println("Could not find package declaration in source file " + source);
                    System.exit(1);
                }

                sourceJarCreator.addEntry(packageName.replace('.', '/') + "/" + sourcePath.getFileName().toString(), sourcePath);
            }

            sourceJarCreator.execute();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
