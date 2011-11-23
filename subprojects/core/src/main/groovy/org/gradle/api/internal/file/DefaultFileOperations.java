/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.file;

import groovy.lang.Closure;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.PathValidation;
import org.gradle.api.file.*;
import org.gradle.api.internal.DescribedReadableResource;
import org.gradle.api.internal.file.archive.TarFileTree;
import org.gradle.api.internal.file.archive.ZipFileTree;
import org.gradle.api.internal.file.archive.compression.Bzip2Archiver;
import org.gradle.api.internal.file.archive.compression.GzipArchiver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.file.copy.*;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.tasks.WorkResult;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.DefaultExecAction;
import org.gradle.process.internal.DefaultJavaExecAction;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.JavaExecAction;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.Map;

import static org.gradle.util.ConfigureUtil.configure;

public class DefaultFileOperations implements FileOperations {
    private final FileResolver fileResolver;
    private final TaskResolver taskResolver;
    private final TemporaryFileProvider temporaryFileProvider;
    private DeleteAction deleteAction;

    public DefaultFileOperations(FileResolver fileResolver, TaskResolver taskResolver, TemporaryFileProvider temporaryFileProvider) {
        this.fileResolver = fileResolver;
        this.taskResolver = taskResolver;
        this.temporaryFileProvider = temporaryFileProvider;
        this.deleteAction = new DeleteActionImpl(fileResolver);
    }

    public File file(Object path) {
        return fileResolver.resolve(path);
    }

    public File file(Object path, PathValidation validation) {
        return fileResolver.resolve(path, validation);
    }

    public URI uri(Object path) {
        return fileResolver.resolveUri(path);
    }
    
    public ConfigurableFileCollection files(Object... paths) {
        return new DefaultConfigurableFileCollection(fileResolver, taskResolver, paths);
    }

    public ConfigurableFileCollection files(Object paths, Closure configureClosure) {
        return configure(configureClosure, files(paths));
    }

    public ConfigurableFileTree fileTree(Object baseDir) {
        return new DefaultConfigurableFileTree(baseDir, fileResolver, taskResolver);
    }

    public ConfigurableFileTree fileTree(Map<String, ?> args) {
        return new DefaultConfigurableFileTree(args, fileResolver, taskResolver);
    }

    public ConfigurableFileTree fileTree(Closure closure) {
        return configure(closure, new DefaultConfigurableFileTree(Collections.emptyMap(), fileResolver, taskResolver));
    }

    public FileTree zipTree(Object zipPath) {
        return new FileTreeAdapter(new ZipFileTree(file(zipPath), getExpandDir()));
    }

    public FileTree tarTree(Object tarPath) {
        //TODO SF - rationalize, refactor if possible
        DescribedReadableResource res;
        if (tarPath instanceof DescribedReadableResource) {
            res = (DescribedReadableResource) tarPath;
        } else if (tarPath instanceof ReadableResource) {
            res = new AnonymousDescribedReadableResource((ReadableResource) tarPath);
        } else {
            res = new MaybeCompressedFileResource(file(tarPath));
        }
        TarFileTree tarTree = new TarFileTree(res, getExpandDir());
        return new FileTreeAdapter(tarTree);
    }

    private File getExpandDir() {
        return temporaryFileProvider.newTemporaryFile("expandedArchives");
    }

    public String relativePath(Object path) {
        return fileResolver.resolveAsRelativePath(path);
    }

    public File mkdir(Object path) {
        File dir = fileResolver.resolve(path);
        if (dir.isFile()) {
            throw new InvalidUserDataException(String.format("Can't create directory. The path=%s points to an existing file.", path));
        }
        dir.mkdirs();
        return dir;
    }

    public boolean delete(Object... paths) {
        return deleteAction.delete(paths);
    }

    public WorkResult copy(Closure closure) {
        CopyActionImpl action = configure(closure, new FileCopyActionImpl(fileResolver, new FileCopySpecVisitor()));
        action.execute();
        return action;
    }

    public CopySpec copySpec(Closure closure) {
        return configure(closure, new CopySpecImpl(fileResolver));
    }

    public FileResolver getFileResolver() {
        return fileResolver;
    }

    public DeleteAction getDeleteAction() {
        return deleteAction;
    }

    public void setDeleteAction(DeleteAction deleteAction) {
        this.deleteAction = deleteAction;
    }

    public ExecResult javaexec(Closure cl) {
        JavaExecAction javaExecAction = ConfigureUtil.configure(cl, new DefaultJavaExecAction(fileResolver));
        return javaExecAction.execute();
    }

    public ExecResult exec(Closure cl) {
        ExecAction execAction = ConfigureUtil.configure(cl, new DefaultExecAction(fileResolver));
        return execAction.execute();
    }

    public ReadableResource gzip(Object path) {
        return new GzipArchiver(new FileResource(file(path)));
    }

    public ReadableResource bzip2(Object path) {
        return new Bzip2Archiver(new FileResource(file(path)));
    }
}
