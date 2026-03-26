package top.fusb.deploybot.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

@Service
public class DeploymentBackupService {

    public boolean snapshotDirectory(Path sourceDir, Path backupDir) throws IOException {
        if (sourceDir == null || !Files.exists(sourceDir)) {
            return false;
        }
        if (!Files.isDirectory(sourceDir)) {
            throw new IOException("只能备份目录类型的部署目标: " + sourceDir);
        }

        deleteRecursively(backupDir);
        Files.createDirectories(backupDir);
        copyDirectoryContents(sourceDir, backupDir);
        return true;
    }

    public void restoreDirectory(Path backupDir, Path targetDir) throws IOException {
        if (backupDir == null || !Files.exists(backupDir) || !Files.isDirectory(backupDir)) {
            throw new IOException("未找到可恢复的备份目录: " + backupDir);
        }
        Files.createDirectories(targetDir);
        deleteDirectoryContents(targetDir);
        copyDirectoryContents(backupDir, targetDir);
    }

    private void copyDirectoryContents(Path sourceDir, Path targetDir) throws IOException {
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = sourceDir.relativize(dir);
                Path destinationDir = targetDir.resolve(relative);
                Files.createDirectories(destinationDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = sourceDir.relativize(file);
                Path destinationFile = targetDir.resolve(relative);
                Files.copy(file, destinationFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteDirectoryContents(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory) || !Files.isDirectory(directory)) {
            return;
        }
        try (var children = Files.list(directory)) {
            for (Path child : children.toList()) {
                deleteRecursively(child);
            }
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
