package org.jenkinsci.plugins.gitlabmergerequestbuilder;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.GitlabCause;
import hudson.model.Project;
import hudson.model.UnprotectedRootAction;
import hudson.plugins.git.RevisionParameterAction;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import net.sf.json.JSONObject;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.logging.Logger;

import static org.jenkinsci.plugins.gitlabmergerequestbuilder.Utils.*;

@Extension
public class BuildPushAction implements UnprotectedRootAction {

    private static final Logger logger = Logger.getLogger(BuildPushAction.class.getName());

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "/push";
    }


    public void doBuild(StaplerRequest req, StaplerResponse rsp) throws IOException, URISyntaxException {
        logger.info("Building Push");

        String reqJson = slurp(req);
        logger.info("Request JSON: " + reqJson);

        JSONObject json = JSONObject.fromObject(reqJson);
        String uri = getStringOrNull(json, "uri");
        String sha = getStringOrNull(json, "sha");
        String branch = getStringOrNull(json, "branch");
        String buildId = getStringOrNull(json, "build_id");

        Project project = findExistingProject(new URIish(uri), branch);
        if (project == null) {
            logger.info("No projects found with repository [" + uri + "]");
            return;
        }

        logger.info("Found existing project [" + project.getName() + "]");

        GitlabCause cause = new GitlabCause(buildId, md5(buildId, uri, sha));

        boolean redisNotifierFound = false;
        DescribableList<Publisher,Descriptor<Publisher>> publishersList = project.getPublishersList();
        for (Publisher publisher : publishersList) {
            if (publisher instanceof RedisNotifier) {
                redisNotifierFound = true;
                break;
            }
        }
        if (!redisNotifierFound) {
            logger.info("No RedisNotifier found in project. Adding one.");
            project.getPublishersList().add(new RedisNotifier());
        } else {
            logger.info("RedisNotifier already present");
        }

        project.scheduleBuild(0, cause, new RevisionParameterAction(sha, false));

        rsp.setStatus(HttpServletResponse.SC_OK);
    }

}
