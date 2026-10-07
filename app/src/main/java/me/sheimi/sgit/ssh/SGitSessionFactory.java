package me.sheimi.sgit.ssh;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import org.eclipse.jgit.transport.CredentialsProviderUserInfo;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import me.sheimi.sgit.MGitApplication;

/**
 * Custom config for Jsch, including using user-provided private keys
 */
public class SGitSessionFactory extends JschConfigSessionFactory {

    /**
     * Asset holding the only host keys we trust (GitHub's published keys).
     * Connections to any other host, or to a host presenting a different key, are rejected.
     */
    static final String KNOWN_HOSTS_ASSET = "github_known_hosts";

    @Override
    protected void configure(Host host, Session session) {
        session.setConfig("StrictHostKeyChecking", "yes");
        session.setConfig("PreferredAuthentications", "publickey");

        // Awful use of App singleton but not really any other way to get hold of a provider that needs
        // to have been initialised with an Android context
        UserInfo userInfo = new CredentialsProviderUserInfo(session, MGitApplication.getJschCredentialsProvider());
        session.setUserInfo(userInfo);
    }


    @Override
    protected JSch createDefaultJSch(FS fs) throws JSchException {
        JSch jsch = new JSch();
        try (InputStream knownHosts = MGitApplication.getContext().getAssets().open(KNOWN_HOSTS_ASSET)) {
            jsch.setKnownHosts(knownHosts);
        } catch (IOException e) {
            throw new JSchException("Could not load pinned host keys", e);
        }
        PrivateKeyUtils.migratePrivateKeys();
        File sshDir = PrivateKeyUtils.getPrivateKeyFolder();
        for (File file : sshDir.listFiles()) {
            KeyPair kpair = KeyPair.load(jsch, file.getAbsolutePath());
            jsch.addIdentity(file.getAbsolutePath());
        }
        return jsch;
    }

}
