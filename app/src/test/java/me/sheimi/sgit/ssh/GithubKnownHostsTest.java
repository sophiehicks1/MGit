package me.sheimi.sgit.ssh;

import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class GithubKnownHostsTest {

    private static final File ASSET = new File("src/main/assets/" + SGitSessionFactory.KNOWN_HOSTS_ASSET);

    private HostKeyRepository mRepository;
    private List<String> mLines;

    @Before
    public void setUp() throws Exception {
        JSch jsch = new JSch();
        jsch.setKnownHosts(ASSET.getPath());
        mRepository = jsch.getHostKeyRepository();
        mLines = Files.readAllLines(ASSET.toPath(), StandardCharsets.US_ASCII);
    }

    private byte[] keyOfType(String type) {
        for (String line : mLines) {
            String[] parts = line.split(" ");
            if (parts[0].equals("github.com") && parts[1].equals(type)) {
                return Base64.getDecoder().decode(parts[2]);
            }
        }
        throw new AssertionError("no github.com key of type " + type);
    }

    @Test
    public void acceptsEveryPublishedGithubKey() {
        for (String type : new String[]{"ssh-ed25519", "ecdsa-sha2-nistp256", "ssh-rsa"}) {
            byte[] key = keyOfType(type);
            assertEquals(type, HostKeyRepository.OK, mRepository.check("github.com", key));
            assertEquals(type, HostKeyRepository.OK, mRepository.check("[ssh.github.com]:443", key));
        }
    }

    @Test
    public void rejectsADifferentKeyForGithub() {
        byte[] key = keyOfType("ssh-ed25519");
        key[key.length - 1] ^= 1;
        assertEquals(HostKeyRepository.CHANGED, mRepository.check("github.com", key));
    }

    @Test
    public void doesNotKnowOtherHosts() {
        assertEquals(HostKeyRepository.NOT_INCLUDED,
            mRepository.check("gitlab.com", keyOfType("ssh-ed25519")));
    }
}
