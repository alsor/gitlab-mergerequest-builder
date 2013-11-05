package hudson.model;


import hudson.Extension;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;

public class RunOnceProject extends Project<RunOnceProject, RunOnceBuild> implements TopLevelItem {

    public RunOnceProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override
    protected Class<RunOnceBuild> getBuildClass() {
        return RunOnceBuild.class;
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    @Restricted(NoExternalUse.class)
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    @Extension(ordinal=1000)
    public static final class DescriptorImpl extends AbstractProjectDescriptor {

        private String redisHost;
        private int redisPort;

        public DescriptorImpl() {
            load();
        }

        public String getDisplayName() {
            return "Merge Requiest Builder Job";
        }

        public RunOnceProject newInstance(ItemGroup parent, String name) {
            return new RunOnceProject(parent,name);
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            redisHost = json.getString("redisHost");
            redisPort = json.getInt("redisPort");

            save();

            return super.configure(req, json);
        }

        public String getRedisHost() {
            return redisHost;
        }

        public int getRedisPort() {
            return redisPort;
        }
    }
}
