package hudson.model;

import java.io.File;
import java.io.IOException;

public class RunOnceBuild extends Build<RunOnceProject, RunOnceBuild> {

    public RunOnceBuild(RunOnceProject project) throws IOException {
        super(project);
    }

    public RunOnceBuild(RunOnceProject project, File buildDir) throws IOException {
        super(project, buildDir);
    }

}
