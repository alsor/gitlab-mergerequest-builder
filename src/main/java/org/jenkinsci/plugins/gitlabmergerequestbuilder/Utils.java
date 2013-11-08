package org.jenkinsci.plugins.gitlabmergerequestbuilder;

import hudson.model.Project;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.stapler.StaplerRequest;

import java.io.BufferedReader;
import java.io.IOException;

public class Utils {

    public static String escape(String s) {
        StringBuffer sb = new StringBuffer();
        final int len = s.length();
        for(int i=0;i<len;i++){
            char ch=s.charAt(i);
            switch(ch){
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if(isUnicodeEncoded(ch)){
                        String ss=Integer.toHexString(ch);
                        sb.append("\\u");
                        for(int k=0;k<4-ss.length();k++){
                            sb.append('0');
                        }
                        sb.append(ss.toUpperCase());
                    }
                    else{
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }

    private static boolean isUnicodeEncoded(char ch) {
        return (ch>='\u0000' && ch<='\u001F') ||
                (ch >= '\u007F' && ch<='\u009F') ||
                (ch >= '\u2000' && ch<='\u20FF');
    }

    public static Project findProjectByUri(URIish toFind) {
        for (Project project : Jenkins.getInstance().getAllItems(Project.class)) {
            SCM scm = project.getScm();
            if (scm instanceof GitSCM) {
                GitSCM gitSCM = (GitSCM) scm;
                for (RemoteConfig repository : gitSCM.getRepositories()) {
                    for (URIish existing : repository.getURIs()) {
                        if (looselyMatches(existing, toFind)) {
                            return project;
                        }
                    }

                }
            }
        }

        return null;
    }

    public static boolean looselyMatches(URIish lhs, URIish rhs) {
        return StringUtils.equals(lhs.getHost(), rhs.getHost())
                && StringUtils.equals(normalizePath(lhs.getPath()), normalizePath(rhs.getPath()));
    }

    public static String normalizePath(String path) {
        if (path.startsWith("/"))   path=path.substring(1);
        if (path.endsWith("/"))     path=path.substring(0,path.length()-1);
        if (path.endsWith(".git"))  path=path.substring(0,path.length()-4);
        return path;
    }

    public static String slurp(StaplerRequest req) throws IOException {
        BufferedReader reader = req.getReader();
        StringBuilder builder = new StringBuilder();
        String aux;

        while ((aux = reader.readLine()) != null) {
            builder.append(aux);
        }

        return builder.toString();
    }

    public static String getStringOrNull(JSONObject json, String key) {
        Object o = json.get(key);
        if (o !=null){
            return o.toString();
        }
        return null;
    }

    public static String md5(String... ss) {
        StringBuilder sb = new StringBuilder();
        for (String s : ss) {
            if (s != null) {
                sb.append(s);
            }
        }

        return DigestUtils.md5Hex(sb.toString());
    }
}
