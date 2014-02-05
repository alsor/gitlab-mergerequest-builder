package org.jenkinsci.plugins.gitlabmergerequestbuilder;

import hudson.Extension;
import hudson.model.GitlabCause;
import hudson.model.Project;
import hudson.model.RunOnceProject;
import hudson.model.UnprotectedRootAction;
import hudson.plugins.git.*;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import hudson.tasks.junit.JUnitResultArchiver;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static org.jenkinsci.plugins.gitlabmergerequestbuilder.Utils.*;


@Extension
public class BuildMergeRequestAction implements UnprotectedRootAction {

    private static final Logger logger = Logger.getLogger(BuildMergeRequestAction.class.getName());
    private static final String TARGET_REPO = "targetRepo";
    private static final String SOURCE_REPO = "sourceRepo";

    private static final String DEFAULT_COMMANDS = "#!/bin/bash -i\n" +
            "rvm use \"ruby-1.9.3\"\n" +
            "cp config/database.sample.yml config/database.yml\n" +
            "RAILS_ENV=test bundle install\n" +
            "RAILS_ENV=test COVERAGE=true bundle exec rake ci:test";

    public static final String DEFAULT_TEST_RESULTS_LOCATION = "test/reports/*.xml";

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "/merge-request";
    }


    private static String createUniqueName(String buildId) {
        String base = "Build Merge Request #" + buildId;
        int suffix = 1;
        String name = base;
        while (Jenkins.getInstance().getItem(name) != null) {
            suffix++;
            name = base + "(" + suffix + ")";
        }
        return name;
    }

    public void doBuild(StaplerRequest req, StaplerResponse rsp) throws IOException, URISyntaxException {
        logger.info("Building merge request");

        String reqJson = slurp(req);
        logger.info("Request JSON: " + reqJson);

        JSONObject json = JSONObject.fromObject(reqJson);

        String buildId = getStringOrNull(json, "build_id");
        String targetBranch = getStringOrNull(json, "target_branch");
        String sourceBranch = getStringOrNull(json, "source_branch");
        String sourceSha = getStringOrNull(json, "source_sha");
        String targetUri = getStringOrNull(json, "target_uri");
        String sourceUri = getStringOrNull(json, "source_uri");

        Project existingProject = findExistingProject(new URIish(targetUri), targetBranch);

        RunOnceProject.DescriptorImpl descriptor = Jenkins.getInstance().
                getDescriptorByType(RunOnceProject.DescriptorImpl.class);

        String name = createUniqueName(buildId);
        logger.info("Creating RunOnceProject with name [" + name + "]");
        RunOnceProject project = (RunOnceProject) Jenkins.getInstance().createProject(descriptor, name);

        List<UserRemoteConfig> userRemoteConfigs = new ArrayList<UserRemoteConfig>();
        userRemoteConfigs.add(new UserRemoteConfig(targetUri, TARGET_REPO, null));
        userRemoteConfigs.add(new UserRemoteConfig(sourceUri, SOURCE_REPO, null));

        List<BranchSpec> branches = new ArrayList<BranchSpec>();
        branches.add(new BranchSpec(SOURCE_REPO + "/" + sourceBranch));

        UserMergeOptions userMergeOptions = new UserMergeOptions(TARGET_REPO, targetBranch);

        GitSCM scm = new GitSCM(null, userRemoteConfigs, branches,
                userMergeOptions,
                false, Collections.<SubmoduleConfig>emptyList(), false,
                false, new DefaultBuildChooser(), null, null, false, null,
                null,
                null, null, null, false, false, false, false, null, null, false, null, false, false);

        project.setScm(scm);

        if (existingProject != null) {
            logger.info("Found existing project [" + existingProject.getName() + "]");
            project.setAssignedLabel(existingProject.getAssignedLabel());

            for (Object item : existingProject.getBuilders()) {
                project.getBuildersList().add((Builder) item);
            }

            for (Object p : existingProject.getPublishersList()) {
                if (p instanceof JUnitResultArchiver) {
                    JUnitResultArchiver testResultPublisher = (JUnitResultArchiver) p;
                    logger.info("Found test results publisher in existing project. Will collect from ["
                            + testResultPublisher.getTestResults() + "]");
                    project.getPublishersList().add(testResultPublisher);
                }
            }

        } else {
            project.getBuildersList().add(new Shell(DEFAULT_COMMANDS));
            project.getPublishersList().add(new JUnitResultArchiver(DEFAULT_TEST_RESULTS_LOCATION, false, null));
        }

        String md5 = md5(buildId, targetBranch, sourceBranch, sourceSha, targetUri, sourceUri);
        GitlabCause cause = new GitlabCause(buildId, md5);

        project.getPublishersList().add(new RedisNotifier());

        if (sourceSha != null && sourceSha.trim() != "") {
            project.scheduleBuild(0, cause, new RevisionParameterAction(sourceSha, false));
        } else {
            project.scheduleBuild(cause);
        }

        rsp.setStatus(HttpServletResponse.SC_OK);
    }

}
