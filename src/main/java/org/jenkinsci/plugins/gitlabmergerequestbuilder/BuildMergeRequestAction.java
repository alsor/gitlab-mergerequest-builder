package org.jenkinsci.plugins.gitlabmergerequestbuilder;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.git.*;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.scm.SCM;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.tasks.Notifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import org.kohsuke.stapler.export.*;
import redis.clients.jedis.Jedis;

@Extension
public class BuildMergeRequestAction implements RootAction {

    private static final Logger logger = Logger.getLogger(BuildMergeRequestAction.class.getName());
    private static final String TARGET_REPO = "targetRepo";
    private static final String SOURCE_REPO = "sourceRepo";

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "/merge-request";
    }


    public void doBuild(StaplerRequest req, StaplerResponse rsp) throws IOException, URISyntaxException {

        JSONObject json = JSONObject.fromObject(slurp(req));

        String buildId = json.getString("build_id");
        String targetSha = json.getString("target_sha");
        String sourceSha = json.getString("source_sha");
        String targetUri = json.getString("target_uri");
        String sourceUri = json.getString("source_uri");

        String name = "p2";
        RunOnceProject project = (RunOnceProject) Jenkins.getInstance().createProject(RunOnceProject.DESCRIPTOR, name);

        List<UserRemoteConfig> userRemoteConfigs = new ArrayList<UserRemoteConfig>();
        userRemoteConfigs.add(new UserRemoteConfig(targetUri, TARGET_REPO, null));
        userRemoteConfigs.add(new UserRemoteConfig(sourceUri, SOURCE_REPO, null));

        List<BranchSpec> branches = new ArrayList<BranchSpec>();
        branches.add(new BranchSpec(SOURCE_REPO + "/" + sourceSha));

        UserMergeOptions userMergeOptions = new UserMergeOptions(TARGET_REPO, targetSha);

        GitSCM scm = new GitSCM(null, userRemoteConfigs, branches,
                userMergeOptions,
                false, Collections.<SubmoduleConfig>emptyList(), false,
                false, new DefaultBuildChooser(), null, null, false, null,
                null,
                null, null, null, false, false, false, false, null, null, false, null, false, false);


        project.setScm(scm);

        Project existingProject = findProjectByUri(new URIish(targetUri));
        if (existingProject != null) {
            project.setAssignedLabel(existingProject.getAssignedLabel());

            for (Object item : existingProject.getBuilders()) {
                 project.getBuildersList().add((Builder) item);
            }
        } else {
            String defaultCommands = "#!/bin/bash -i\n" +
                    "rvm use \"ruby-1.9.3\"\n" +
                    "ENV=test bundle install\n" +
                    "COVERAGE=true ENV=test bundle exec rake spec";
            project.getBuildersList().add(new Shell(defaultCommands));
        }


        project.getPublishersList().add(new Notifier() {
            public BuildStepMonitor getRequiredMonitorService() {
                return BuildStepMonitor.NONE;
            }

            @Override
            public boolean perform(AbstractBuild<?, ?> abstractBuild, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

                Build build = (Build) abstractBuild;

                Jedis jedis = new Jedis("localhost");

                ModelBuilder modelBuilder = new ModelBuilder();
                StringWriter w = new StringWriter();
                Model<Build> buildModel = modelBuilder.get(Build.class);
                TreePruner pruner = new TreePruner.ByDepth(Integer.MAX_VALUE);
                DataWriter dataWriter = Flavor.JSON.createDataWriter(build, w);
                buildModel.writeTo(build, pruner, dataWriter);


                jedis.rpush("resque:gitlab:queue:build_result", w.toString());

                return true;
            }
        });

        project.scheduleBuild(null);

        rsp.getWriter().println("merge request id: " + json.get("mergeRequestId"));
    }

    private static Project findProjectByUri(URIish toFind) {
        for (Project project : Jenkins.getInstance().getAllItems(Project.class)) {
            SCM scm = project.getScm();
            if (scm instanceof GitSCM) {
                GitSCM gitSCM = (GitSCM) scm;
                for (RemoteConfig repository : gitSCM.getRepositories()) {
                    for (URIish existing : repository.getURIs()) {
                        if (GitStatus.looselyMatches(existing, toFind)) {
                            return project;
                        }
                    }

                }
            }
        }

        return null;
    }

    private static String slurp(StaplerRequest req) throws IOException {
        BufferedReader reader = req.getReader();
        StringBuilder builder = new StringBuilder();
        String aux;

        while ((aux = reader.readLine()) != null) {
            builder.append(aux);
        }

        return builder.toString();
    }
}
