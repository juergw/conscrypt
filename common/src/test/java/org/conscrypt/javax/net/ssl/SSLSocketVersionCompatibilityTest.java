/*
 * Copyright (C) 2010 The Android Open Source Project
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

package org.conscrypt.javax.net.ssl;

import static org.conscrypt.TestUtils.UTF_8;
import static org.conscrypt.TestUtils.isLinux;
import static org.conscrypt.TestUtils.isOsx;
import static org.conscrypt.TestUtils.isTlsV1Deprecated;
import static org.conscrypt.TestUtils.isTlsV1Filtered;
import static org.conscrypt.TestUtils.isTlsV1Supported;
import static org.conscrypt.TestUtils.isWindows;
import static org.conscrypt.TestUtils.osName;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import org.conscrypt.Conscrypt;
import org.conscrypt.TestUtils;
import org.conscrypt.java.security.StandardNames;
import org.conscrypt.java.security.TestKeyStore;
import org.conscrypt.testing.OpaqueProvider;
import org.conscrypt.tlswire.TlsTester;
import org.conscrypt.tlswire.handshake.AlpnHelloExtension;
import org.conscrypt.tlswire.handshake.ClientHello;
import org.conscrypt.tlswire.handshake.HandshakeMessage;
import org.conscrypt.tlswire.handshake.HelloExtension;
import org.conscrypt.tlswire.handshake.ServerNameHelloExtension;
import org.conscrypt.tlswire.record.TlsProtocols;
import org.conscrypt.tlswire.record.TlsRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.StandardConstants;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import tests.net.DelegatingSSLSocketFactory;
import tests.util.ForEachRunner;
import tests.util.Pair;

/**
 * Tests for SSLSocket classes that ensure the TLS 1.2 and TLS 1.3 implementations
 * are compatible.
 */
@RunWith(Parameterized.class)
public class SSLSocketVersionCompatibilityTest {
    @Parameterized.Parameters(name = "{index}: {0} client, {1} server")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { "TLSv1.2", "TLSv1.2" },
            { "TLSv1.2", "TLSv1.3" },
            { "TLSv1.3", "TLSv1.2" },
            { "TLSv1.3", "TLSv1.3" },
        });
    }

    private final String clientVersion;
    private final String serverVersion;
    private ExecutorService executor;
    private ThreadGroup threadGroup;

    public SSLSocketVersionCompatibilityTest(String clientVersion, String serverVersion) {
        this.clientVersion = clientVersion;
        this.serverVersion = serverVersion;
    }

    @Before
    public void setup() {
        threadGroup = new ThreadGroup("SSLSocketVersionedTest");
        executor = Executors.newCachedThreadPool(r -> new Thread(threadGroup, r));
    }

    @After
    public void teardown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void test_SSLSocket_startHandshake() throws Exception {
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion).build();
        SSLSocket client =
                (SSLSocket) c.clientContext.getSocketFactory().createSocket(c.host, c.port);
        final SSLSocket server = (SSLSocket) c.serverSocket.accept();
        Future<Void> future = runAsync(() -> {
            server.startHandshake();
            assertNotNull(server.getSession());
            assertNull(server.getHandshakeSession());
            try {
                server.getSession().getPeerCertificates();
                fail();
            } catch (SSLPeerUnverifiedException expected) {
                // Ignored.
            }
            Certificate[] localCertificates = server.getSession().getLocalCertificates();
            assertNotNull(localCertificates);
            TestKeyStore.assertChainLength(localCertificates);
            assertNotNull(localCertificates[0]);
            TestSSLContext
                .assertServerCertificateChain(c.serverTrustManager, localCertificates);
            TestSSLContext.assertCertificateInKeyStore(localCertificates[0], c.serverKeyStore);
            return null;
        });
        client.startHandshake();
        assertNotNull(client.getSession());
        assertNull(client.getSession().getLocalCertificates());
        Certificate[] peerCertificates = client.getSession().getPeerCertificates();
        assertNotNull(peerCertificates);
        TestKeyStore.assertChainLength(peerCertificates);
        assertNotNull(peerCertificates[0]);
        TestSSLContext.assertServerCertificateChain(c.clientTrustManager, peerCertificates);
        TestSSLContext.assertCertificateInKeyStore(peerCertificates[0], c.serverKeyStore);
        future.get();
        client.close();
        server.close();
        c.close();
    }
    private static final class SSLServerSessionIdCallable implements Callable<byte[]> {
        private final SSLSocket server;
        private SSLServerSessionIdCallable(SSLSocket server) {
            this.server = server;
        }
        @Override
        public byte[] call() throws Exception {
            server.startHandshake();
            assertNotNull(server.getSession());
            assertNotNull(server.getSession().getId());
            return server.getSession().getId();
        }
    }

    @Test
    public void test_SSLSocket_confirmSessionReuse() throws Exception {
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        final SSLSocket client1 = (SSLSocket) c.clientContext.getSocketFactory().createSocket(
                c.host.getHostName(), c.port);
        final SSLSocket server1 = (SSLSocket) c.serverSocket.accept();
        final Future<byte[]> future1 = runAsync(new SSLServerSessionIdCallable(server1));
        client1.startHandshake();
        assertNotNull(client1.getSession());
        assertNotNull(client1.getSession().getId());
        final byte[] clientSessionId1 = client1.getSession().getId();
        final byte[] serverSessionId1 = future1.get();
        assertArrayEquals(clientSessionId1, serverSessionId1);
        client1.close();
        server1.close();
        final SSLSocket client2 = (SSLSocket) c.clientContext.getSocketFactory().createSocket(
                c.host.getHostName(), c.port);
        final SSLSocket server2 = (SSLSocket) c.serverSocket.accept();
        final Future<byte[]> future2 = runAsync(new SSLServerSessionIdCallable(server2));
        client2.startHandshake();
        assertNotNull(client2.getSession());
        assertNotNull(client2.getSession().getId());
        final byte[] clientSessionId2 = client2.getSession().getId();
        final byte[] serverSessionId2 = future2.get();
        assertArrayEquals(clientSessionId2, serverSessionId2);
        client2.close();
        server2.close();
        assertArrayEquals(clientSessionId1, clientSessionId2);
        c.close();
    }

    @Test
    public void test_SSLSocket_NoEnabledCipherSuites_Failure() throws Exception {
        TestSSLContext c = TestSSLContext.newBuilder()
                .useDefaults(false)
                .clientContext(defaultInit(SSLContext.getInstance(clientVersion)))
                .serverContext(defaultInit(SSLContext.getInstance(serverVersion)))
                .build();
        SSLSocket client =
                (SSLSocket) c.clientContext.getSocketFactory().createSocket(c.host, c.port);
        client.setEnabledCipherSuites(new String[0]);
        final SSLSocket server = (SSLSocket) c.serverSocket.accept();
        Future<Void> future = runAsync(() -> {
            try {
                server.startHandshake();
                fail();
            } catch (SSLHandshakeException expected) {
                // Ignored.
            }
            return null;
        });
        try {
            client.startHandshake();
            fail();
        } catch (SSLHandshakeException expected) {
            // Ignored.
        }
        future.get();
        server.close();
        client.close();
        c.close();
    }

    @Test
    public void test_SSLSocket_startHandshake_noKeyStore() throws Exception {
        TestSSLContext c = TestSSLContext.newBuilder()
                .useDefaults(false)
                .clientContext(defaultInit(SSLContext.getInstance(clientVersion)))
                .serverContext(defaultInit(SSLContext.getInstance(serverVersion)))
                .build();
        SSLSocket client =
                (SSLSocket) c.clientContext.getSocketFactory().createSocket(c.host, c.port);
        final SSLSocket server = (SSLSocket) c.serverSocket.accept();
        Future<Void> future = runAsync(() -> {
            try {
                server.startHandshake();
                fail();
            } catch (SSLHandshakeException expected) {
                // Ignored.
            }
            return null;
        });
        try {
            client.startHandshake();
            fail();
        } catch (SSLHandshakeException expected) {
            // Ignored.
        }
        future.get();
        server.close();
        client.close();
        c.close();
    }

    @Test
    public void test_SSLSocket_startHandshake_noClientCertificate() throws Exception {
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        SSLContext clientContext = c.clientContext;
        SSLSocket client =
                (SSLSocket) clientContext.getSocketFactory().createSocket(c.host, c.port);
        final SSLSocket server = (SSLSocket) c.serverSocket.accept();
        Future<Void> future = runAsync(() -> {
            server.startHandshake();
            return null;
        });
        client.startHandshake();
        future.get();
        client.close();
        server.close();
        c.close();
    }

    @Test
    @SuppressWarnings("deprecation")
    public void test_SSLSocket_HandshakeCompletedListener() throws Exception {
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        final SSLSocket client =
                (SSLSocket) c.clientContext.getSocketFactory().createSocket(c.host, c.port);
        final SSLSocket server = (SSLSocket) c.serverSocket.accept();
        Future<Void> future = runAsync(() -> {
            server.startHandshake();
            return null;
        });
        final boolean[] handshakeCompletedListenerCalled = new boolean[1];
        client.addHandshakeCompletedListener(new HandshakeCompletedListener() {
            @Override
            public void handshakeCompleted(HandshakeCompletedEvent event) {
                SSLSocket socket = null;
                try {
                    SSLSession session = event.getSession();
                    String cipherSuite = event.getCipherSuite();
                    Certificate[] localCertificates = event.getLocalCertificates();
                    Certificate[] peerCertificates = event.getPeerCertificates();
                    Principal peerPrincipal = event.getPeerPrincipal();
                    Principal localPrincipal = event.getLocalPrincipal();
                    socket = event.getSocket();
                    assertNotNull(session);
                    byte[] id = session.getId();
                    assertNotNull(id);
                    if (negotiatedVersion().equals("TLSv1.2")) {
                        // Session ticket delivery happens inside the handshake in TLS 1.2,
                        // but outside it for TLS 1.3.
                        assertEquals(32, id.length);
                        assertNotNull(c.clientContext.getClientSessionContext().getSession(id));
                    } else {
                        assertEquals(0, id.length);
                    }
                    assertNotNull(cipherSuite);
                    assertTrue(Arrays.asList(client.getEnabledCipherSuites())
                            .contains(cipherSuite));
                    assertTrue(Arrays.asList(c.serverSocket.getEnabledCipherSuites())
                            .contains(cipherSuite));

                    assertNull(localCertificates);
                    assertNotNull(peerCertificates);
                    TestKeyStore.assertChainLength(peerCertificates);
                    assertNotNull(peerCertificates[0]);
                    TestSSLContext
                        .assertServerCertificateChain(c.clientTrustManager, peerCertificates);
                    TestSSLContext
                        .assertCertificateInKeyStore(peerCertificates[0], c.serverKeyStore);
                    assertNotNull(peerPrincipal);
                    TestSSLContext.assertCertificateInKeyStore(peerPrincipal, c.serverKeyStore);
                    assertNull(localPrincipal);
                    assertNotNull(socket);
                    assertSame(client, socket);
                    assertNull(socket.getHandshakeSession());
                    if (TestUtils.isJavaxCertificateSupported()) {
                        javax.security.cert.X509Certificate[] peerCertificateChain =
                                event.getPeerCertificateChain();
                        assertNotNull(peerCertificateChain);
                        TestKeyStore.assertChainLength(peerCertificateChain);
                        assertNotNull(peerCertificateChain[0]);
                        TestSSLContext.assertCertificateInKeyStore(
                                peerCertificateChain[0].getSubjectDN(), c.serverKeyStore);
                    }
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    synchronized (handshakeCompletedListenerCalled) {
                        handshakeCompletedListenerCalled[0] = true;
                        handshakeCompletedListenerCalled.notify();
                    }
                    handshakeCompletedListenerCalled[0] = true;
                    if (socket != null) {
                        socket.removeHandshakeCompletedListener(this);
                    }
                }
            }
        });
        client.startHandshake();
        future.get();
        if (negotiatedVersion().equals("TLSv1.2")) {
            assertNotNull(
                    c.serverContext.getServerSessionContext()
                            .getSession(client.getSession().getId()));
        }
        synchronized (handshakeCompletedListenerCalled) {
            while (!handshakeCompletedListenerCalled[0]) {
                handshakeCompletedListenerCalled.wait();
            }
        }
        client.close();
        server.close();
        c.close();
    }
    private static final class TestUncaughtExceptionHandler implements UncaughtExceptionHandler {
        Throwable actualException;
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            assertNull(actualException);
            actualException = ex;
        }
    }

    @Test
    public void test_SSLSocket_HandshakeCompletedListener_RuntimeException() throws Exception {
        final Thread self = Thread.currentThread();
        final UncaughtExceptionHandler original = self.getUncaughtExceptionHandler();
        final RuntimeException expectedException = new RuntimeException("expected");
        final TestUncaughtExceptionHandler test = new TestUncaughtExceptionHandler();
        self.setUncaughtExceptionHandler(test);
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        final SSLSocket client =
                (SSLSocket) c.clientContext.getSocketFactory().createSocket(c.host, c.port);
        final SSLSocket server = (SSLSocket) c.serverSocket.accept();
        Future<Void> future = runAsync(() -> {
            server.startHandshake();
            return null;
        });
        client.addHandshakeCompletedListener(event -> {
            throw expectedException;
        });
        client.startHandshake();
        future.get();
        client.close();
        server.close();
        c.close();
        assertSame(expectedException, test.actualException);
        self.setUncaughtExceptionHandler(original);
    }

    @Test
    public void test_SSLSocket_getUseClientMode() throws Exception {
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        SSLSocket client =
                (SSLSocket) c.clientContext.getSocketFactory().createSocket(c.host, c.port);
        SSLSocket server = (SSLSocket) c.serverSocket.accept();
        assertTrue(client.getUseClientMode());
        assertFalse(server.getUseClientMode());
        client.close();
        server.close();
        c.close();
    }

    @Test
    public void testClientMode_normal() throws Exception {
        // Client is client and server is server.
        test_SSLSocket_setUseClientMode(true, false);
    }

    @Test
    public void testClientMode_reverse() throws Exception {
        // Client is server and server is client.
        assertThrows(
                SSLHandshakeException.class, () -> test_SSLSocket_setUseClientMode(false, true));
    }

    @Test
    public void testClientMode_bothClient() throws Exception {
        assertThrows(
                SSLHandshakeException.class, () -> test_SSLSocket_setUseClientMode(true, true));
    }

    @Test
    public void testClientMode_bothServer() throws Exception {
        try {
            test_SSLSocket_setUseClientMode(false, false);
            fail();
        } catch (SocketTimeoutException expected) {
            // Ignore
        } catch (SSLHandshakeException expected) {
            // Depending on the timing of the socket closures, this can happen as well.
            assertTrue("Unexpected handshake error: " + expected.getMessage(),
                    expected.getMessage().toLowerCase(Locale.ROOT).contains("connection closed"));
        }
    }

    private void test_SSLSocket_setUseClientMode(
            final boolean clientClientMode, final boolean serverClientMode) throws Exception {
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        SSLSocket client =
                (SSLSocket) c.clientContext.getSocketFactory().createSocket(c.host, c.port);
        final SSLSocket server = (SSLSocket) c.serverSocket.accept();
        Future<IOException> future = runAsync(() -> {
            try {
                if (!serverClientMode) {
                    server.setSoTimeout(1000);
                }
                server.setUseClientMode(serverClientMode);
                server.startHandshake();
                return null;
            } catch (SSLHandshakeException | SocketTimeoutException e) {
                return e;
            }
        });
        if (!clientClientMode) {
            client.setSoTimeout(1000);
        }
        client.setUseClientMode(clientClientMode);
        client.startHandshake();
        IOException ioe = future.get();
        if (ioe != null) {
            throw ioe;
        }
        client.close();
        server.close();
        c.close();
    }

    @Test
    public void test_SSLSocket_clientAuth() throws Exception {
        TestSSLContext c = new TestSSLContext.Builder()
                .client(TestKeyStore.getClientCertificate())
                .server(TestKeyStore.getServer())
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        SSLSocket client =
                (SSLSocket) c.clientContext.getSocketFactory().createSocket(c.host, c.port);
        final SSLSocket server = (SSLSocket) c.serverSocket.accept();
        Future<Void> future = runAsync(() -> {
            assertFalse(server.getWantClientAuth());
            assertFalse(server.getNeedClientAuth());
            // confirm turning one on by itself
            server.setWantClientAuth(true);
            assertTrue(server.getWantClientAuth());
            assertFalse(server.getNeedClientAuth());
            // confirm turning setting on toggles the other
            server.setNeedClientAuth(true);
            assertFalse(server.getWantClientAuth());
            assertTrue(server.getNeedClientAuth());
            // confirm toggling back
            server.setWantClientAuth(true);
            assertTrue(server.getWantClientAuth());
            assertFalse(server.getNeedClientAuth());
            server.startHandshake();
            return null;
        });
        client.startHandshake();
        assertNotNull(client.getSession().getLocalCertificates());
        TestKeyStore.assertChainLength(client.getSession().getLocalCertificates());
        TestSSLContext.assertClientCertificateChain(
                c.clientTrustManager, client.getSession().getLocalCertificates());
        future.get();
        client.close();
        server.close();
        c.close();
    }

    @Test
    public void test_SSLSocket_clientAuth_bogusAlias() throws Exception {
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        SSLContext clientContext = SSLContext.getInstance(clientVersion);
        X509ExtendedKeyManager keyManager = new X509ExtendedKeyManager() {
            @Override
            public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
                return "bogus";
            }
            @Override
            public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                throw new AssertionError();
            }
            @Override
            public X509Certificate[] getCertificateChain(String alias) {
                // return null for "bogus" alias
                return null;
            }
            @Override
            public String[] getClientAliases(String keyType, Principal[] issuers) {
                throw new AssertionError();
            }
            @Override
            public String[] getServerAliases(String keyType, Principal[] issuers) {
                throw new AssertionError();
            }
            @Override
            public String chooseEngineClientAlias(String[] keyType, Principal[] issuers,
                SSLEngine engine) {
                throw new AssertionError();
            }
            @Override
            public String chooseEngineServerAlias(String keyType, Principal[] issuers,
                SSLEngine engine) {
                throw new AssertionError();
            }
            @Override
            public PrivateKey getPrivateKey(String alias) {
                // return null for "bogus" alias
                return null;
            }
        };
        clientContext.init(
                new KeyManager[] {keyManager}, new TrustManager[] {c.clientTrustManager}, null);
        SSLSocket client =
                (SSLSocket) clientContext.getSocketFactory().createSocket(c.host, c.port);
        final SSLSocket server = (SSLSocket) c.serverSocket.accept();
        Future<Void> future = runAsync(() -> {
            try {
                server.setNeedClientAuth(true);
                server.startHandshake();
                fail();
            } catch (SSLHandshakeException expected) {
                // Ignored.
            }
            return null;
        });
        try {
            client.startHandshake();
            // In TLS 1.3, the alert will only show up once we try to use the connection, since
            // the client finishes the handshake without feedback from the server
            client.getInputStream().read();
            fail();
        } catch (SSLException expected) {
            // before we would get a NullPointerException from passing
            // due to the null PrivateKey return by the X509KeyManager.
        }
        future.get();
        client.close();
        server.close();
        c.close();
    }

    @Test
    public void test_SSLSocket_clientAuth_OpaqueKey_RSA() throws Exception {
        run_SSLSocket_clientAuth_OpaqueKey(TestKeyStore.getClientCertificate());
    }

    @Test
    public void test_SSLSocket_clientAuth_OpaqueKey_EC_RSA() throws Exception {
        run_SSLSocket_clientAuth_OpaqueKey(TestKeyStore.getClientEcRsaCertificate());
    }

    @Test
    public void test_SSLSocket_clientAuth_OpaqueKey_EC_EC() throws Exception {
        run_SSLSocket_clientAuth_OpaqueKey(TestKeyStore.getClientEcEcCertificate());
    }
    private void run_SSLSocket_clientAuth_OpaqueKey(TestKeyStore keyStore) throws Exception {
        // OpaqueProvider will not be allowed to operate if the VM we're running on
        // requires Oracle signatures for provider jars, since we don't sign the test jar.
        TestUtils.assumeAllowsUnsignedCrypto();
        try {
            Security.insertProviderAt(new OpaqueProvider(), 1);
            final TestSSLContext c = new TestSSLContext.Builder()
                    .client(keyStore)
                    .server(TestKeyStore.getServer())
                    .clientProtocol(clientVersion)
                    .serverProtocol(serverVersion)
                    .build();
            SSLContext clientContext = SSLContext.getInstance("TLS");
            final X509KeyManager delegateKeyManager = (X509KeyManager) c.clientKeyManagers[0];
            X509ExtendedKeyManager keyManager = new X509ExtendedKeyManager() {
                @Override
                public String chooseClientAlias(
                        String[] keyType, Principal[] issuers, Socket socket) {
                    return delegateKeyManager.chooseClientAlias(keyType, issuers, socket);
                }
                @Override
                public String chooseServerAlias(
                        String keyType, Principal[] issuers, Socket socket) {
                    return delegateKeyManager.chooseServerAlias(keyType, issuers, socket);
                }
                @Override
                public X509Certificate[] getCertificateChain(String alias) {
                    return delegateKeyManager.getCertificateChain(alias);
                }
                @Override
                public String[] getClientAliases(String keyType, Principal[] issuers) {
                    return delegateKeyManager.getClientAliases(keyType, issuers);
                }
                @Override
                public String[] getServerAliases(String keyType, Principal[] issuers) {
                    return delegateKeyManager.getServerAliases(keyType, issuers);
                }
                @Override
                public PrivateKey getPrivateKey(String alias) {
                    PrivateKey privKey = delegateKeyManager.getPrivateKey(alias);
                    return OpaqueProvider.wrapKey(privKey);
                }
                @Override
                public String chooseEngineClientAlias(String[] keyType, Principal[] issuers,
                    SSLEngine engine) {
                    throw new AssertionError();
                }
                @Override
                public String chooseEngineServerAlias(String keyType, Principal[] issuers,
                    SSLEngine engine) {
                    throw new AssertionError();
                }
            };
            clientContext.init(
                    new KeyManager[] {keyManager}, new TrustManager[] {c.clientTrustManager}, null);
            SSLSocket client =
                    (SSLSocket) clientContext.getSocketFactory().createSocket(c.host, c.port);
            final SSLSocket server = (SSLSocket) c.serverSocket.accept();
            Future<Void> future = runAsync(() -> {
                server.setNeedClientAuth(true);
                server.startHandshake();
                return null;
            });
            client.startHandshake();
            assertNotNull(client.getSession().getLocalCertificates());
            TestKeyStore.assertChainLength(client.getSession().getLocalCertificates());
            TestSSLContext.assertClientCertificateChain(
                    c.clientTrustManager, client.getSession().getLocalCertificates());
            future.get();
            client.close();
            server.close();
            c.close();
        } finally {
            Security.removeProvider(OpaqueProvider.NAME);
        }
    }

    @Test
    public void test_SSLSocket_TrustManagerRuntimeException() throws Exception {
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        SSLContext clientContext = SSLContext.getInstance("TLS");
        X509TrustManager trustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                throw new AssertionError();
            }
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                throw new RuntimeException(); // throw a RuntimeException from custom TrustManager
            }
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                throw new AssertionError();
            }
        };
        clientContext.init(null, new TrustManager[] {trustManager}, null);
        SSLSocket client =
                (SSLSocket) clientContext.getSocketFactory().createSocket(c.host, c.port);
        final SSLSocket server = (SSLSocket) c.serverSocket.accept();
        Future<Void> future = runAsync(() -> {
            try {
                server.startHandshake();
                fail();
            } catch (SSLHandshakeException expected) {
                // Ignored.
            }
            return null;
        });
        try {
            client.startHandshake();
            fail();
        } catch (SSLHandshakeException expected) {
            // before we would get a RuntimeException from checkServerTrusted.
        }
        future.get();
        client.close();
        server.close();
        c.close();
    }

    @Test
    public void test_SSLSocket_getEnableSessionCreation() throws Exception {
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        SSLSocket client =
                (SSLSocket) c.clientContext.getSocketFactory().createSocket(c.host, c.port);
        SSLSocket server = (SSLSocket) c.serverSocket.accept();
        assertTrue(client.getEnableSessionCreation());
        assertTrue(server.getEnableSessionCreation());
        client.close();
        server.close();
        c.close();
    }

    @Test
    public void test_SSLSocket_setEnableSessionCreation_server() throws Exception {
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        SSLSocket client =
                (SSLSocket) c.clientContext.getSocketFactory().createSocket(c.host, c.port);
        final SSLSocket server = (SSLSocket) c.serverSocket.accept();
        Future<Void> future = runAsync(() -> {
            server.setEnableSessionCreation(false);
            try {
                server.startHandshake();
                fail();
            } catch (SSLException expected) {
                // Ignored.
            }
            return null;
        });
        try {
            client.startHandshake();
            fail();
        } catch (SSLException expected) {
            // Ignored.
        }
        future.get();
        client.close();
        server.close();
        c.close();
    }

    @Test
    public void test_SSLSocket_setEnableSessionCreation_client() throws Exception {
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        SSLSocket client =
                (SSLSocket) c.clientContext.getSocketFactory().createSocket(c.host, c.port);
        final SSLSocket server = (SSLSocket) c.serverSocket.accept();
        Future<Void> future = runAsync(() -> {
            try {
                server.startHandshake();
                fail();
            } catch (SSLException expected) {
                // Ignored.
            }
            return null;
        });
        client.setEnableSessionCreation(false);
        try {
            client.startHandshake();
            fail();
        } catch (SSLException expected) {
            // Ignored.
        }
        future.get();
        client.close();
        server.close();
        c.close();
    }

    @Test
    public void test_SSLSocket_close() throws Exception {
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        TestSSLSocketPair pair = TestSSLSocketPair.create(c).connect();
        SSLSocket server = pair.server;
        SSLSocket client = pair.client;
        assertFalse(server.isClosed());
        assertFalse(client.isClosed());
        InputStream input = client.getInputStream();
        OutputStream output = client.getOutputStream();
        server.close();
        client.close();
        assertTrue(server.isClosed());
        assertTrue(client.isClosed());
        // close after close is okay...
        server.close();
        client.close();
        // ...so are a lot of other operations...
        HandshakeCompletedListener l = e -> { };
        client.addHandshakeCompletedListener(l);
        assertNotNull(client.getEnabledCipherSuites());
        assertNotNull(client.getEnabledProtocols());
        client.getEnableSessionCreation();
        client.getNeedClientAuth();
        assertNotNull(client.getSession());
        assertNotNull(client.getSSLParameters());
        assertNotNull(client.getSupportedProtocols());
        client.getUseClientMode();
        client.getWantClientAuth();
        client.removeHandshakeCompletedListener(l);
        client.setEnabledCipherSuites(new String[0]);
        client.setEnabledProtocols(new String[0]);
        client.setEnableSessionCreation(false);
        client.setNeedClientAuth(false);
        client.setSSLParameters(client.getSSLParameters());
        client.setWantClientAuth(false);
        // ...but some operations are expected to give SocketException...
        try {
            client.startHandshake();
            fail();
        } catch (SocketException expected) {
            // Ignored.
        }
        try {
            client.getInputStream();
            fail();
        } catch (SocketException expected) {
            // Ignored.
        }
        try {
            client.getOutputStream();
            fail();
        } catch (SocketException expected) {
            // Ignored.
        }
        try {
            @SuppressWarnings("unused")
            int value = input.read();
            fail();
        } catch (SocketException expected) {
            // Ignored.
        }
        try {
            @SuppressWarnings("unused")
            int bytesRead = input.read(null, -1, -1);
            fail();
        } catch (NullPointerException | SocketException expected) {
            // Ignored.
        }
        try {
            output.write(-1);
            fail();
        } catch (SocketException expected) {
            // Ignored.
        }
        try {
            output.write(null, -1, -1);
            fail();
        } catch (NullPointerException | SocketException expected) {
            // Ignored.
        }
        // ... and one gives IllegalArgumentException
        try {
            client.setUseClientMode(false);
            fail();
        } catch (IllegalArgumentException expected) {
            // Ignored.
        }
        pair.close();
    }

    @Test
    public void test_SSLSocket_ShutdownInput() throws Exception {
        // Fdsocket throws SslException rather than returning EOF after input shutdown
        // on Windows, but we won't be fixing it as that implementation is already deprecated.
        assumeFalse("Skipping shutdownInput() test on Windows", isWindows());

        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        byte[] buffer = new byte[1];
        TestSSLSocketPair pair = TestSSLSocketPair.create(c).connect();
        SSLSocket server = pair.server;
        SSLSocket client = pair.client;
        assertFalse(server.isClosed());
        assertFalse(client.isClosed());
        InputStream input = client.getInputStream();
        client.shutdownInput();
        assertFalse(client.isClosed());
        assertFalse(server.isClosed());
        // Shutdown after shutdown is not OK
        SocketException exception = assertThrows(SocketException.class, client::shutdownInput);
        assertTrue(exception.getMessage().contains("already shutdown"));

        // The following operations should succeed, same as after close()
        HandshakeCompletedListener listener = e -> { };
        client.addHandshakeCompletedListener(listener);
        assertNotNull(client.getEnabledCipherSuites());
        assertNotNull(client.getEnabledProtocols());
        client.getEnableSessionCreation();
        client.getNeedClientAuth();
        assertNotNull(client.getSession());
        assertNotNull(client.getSSLParameters());
        assertNotNull(client.getSupportedProtocols());
        client.getUseClientMode();
        client.getWantClientAuth();
        client.removeHandshakeCompletedListener(listener);
        client.setEnabledCipherSuites(new String[0]);
        client.setEnabledProtocols(new String[0]);
        client.setEnableSessionCreation(false);
        client.setNeedClientAuth(false);
        client.setSSLParameters(client.getSSLParameters());
        client.setWantClientAuth(false);

        // The following operations should succeed, unlike after close()
        client.startHandshake();
        client.getInputStream();
        client.getOutputStream();
        assertEquals(-1, input.read());
        assertEquals(-1, input.read(buffer));
        assertEquals(0, input.available());

        pair.close();
    }

    @Test
    public void test_SSLSocket_ShutdownOutput() throws Exception {
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        byte[] buffer = new byte[1];
        TestSSLSocketPair pair = TestSSLSocketPair.create(c).connect();
        SSLSocket server = pair.server;
        SSLSocket client = pair.client;
        assertFalse(server.isClosed());
        assertFalse(client.isClosed());
        OutputStream output = client.getOutputStream();
        client.shutdownOutput();
        assertFalse(client.isClosed());
        assertFalse(server.isClosed());
        // Shutdown after shutdown is not OK
        SocketException exception = assertThrows(SocketException.class, client::shutdownOutput);
        assertTrue(exception.getMessage().contains("already shutdown"));

        // The following operations should succeed, same as after close()
        HandshakeCompletedListener listener = e -> { };
        client.addHandshakeCompletedListener(listener);
        assertNotNull(client.getEnabledCipherSuites());
        assertNotNull(client.getEnabledProtocols());
        client.getEnableSessionCreation();
        client.getNeedClientAuth();
        assertNotNull(client.getSession());
        assertNotNull(client.getSSLParameters());
        assertNotNull(client.getSupportedProtocols());
        client.getUseClientMode();
        client.getWantClientAuth();
        client.removeHandshakeCompletedListener(listener);
        client.setEnabledCipherSuites(new String[0]);
        client.setEnabledProtocols(new String[0]);
        client.setEnableSessionCreation(false);
        client.setNeedClientAuth(false);
        client.setSSLParameters(client.getSSLParameters());
        client.setWantClientAuth(false);

        // The following operations should succeed, unlike after close()
        client.startHandshake();
        client.getInputStream();
        client.getOutputStream();

        // Any output should fail
        try {
            output.write(buffer);
            fail();
        } catch (SocketException | SSLException expected) {
            // Expected.
            // SocketException is correct but the old fd-based implementation
            // throws SSLException, and it's not worth changing it at this late stage.
        }
        pair.close();
    }

    /**
     * b/3350645 Test to confirm that an SSLSocket.close() performing
     * an SSL_shutdown does not throw an IOException if the peer
     * socket has been closed.
     */
    @Test
    public void test_SSLSocket_shutdownCloseOnClosedPeer() throws Exception {
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        final Socket underlying = new Socket(c.host, c.port);
        final SSLSocket wrapping = (SSLSocket) c.clientContext.getSocketFactory().createSocket(
                underlying, c.host.getHostName(), c.port, false);
        Future<Void> clientFuture = runAsync(() -> {
            wrapping.startHandshake();
            wrapping.getOutputStream().write(42);
            // close the underlying socket,
            // so that no SSL shutdown is sent
            underlying.close();
            wrapping.close();
            return null;
        });
        SSLSocket server = (SSLSocket) c.serverSocket.accept();
        server.startHandshake();
        @SuppressWarnings("unused")
        int value = server.getInputStream().read();
        // wait for thread to finish so we know client is closed.
        clientFuture.get();
        // close should cause an SSL_shutdown which will fail
        // because the peer has closed, but it shouldn't throw.
        server.close();
    }

    @Test
    public void test_SSLSocket_endpointIdentification_Success() throws Exception {
        TestUtils.assumeSetEndpointIdentificationAlgorithmAvailable();
        // The default hostname verifier on OpenJDK just rejects all hostnames,
        // which is not helpful, so replace with a basic functional one.
        HostnameVerifier oldDefault = HttpsURLConnection.getDefaultHostnameVerifier();
        HttpsURLConnection.setDefaultHostnameVerifier(new TestHostnameVerifier());
        try {
            final TestSSLContext c = new TestSSLContext.Builder()
                    .clientProtocol(clientVersion)
                    .serverProtocol(serverVersion)
                    .build();
            SSLSocket client = (SSLSocket) c.clientContext.getSocketFactory().createSocket();
            SSLParameters p = client.getSSLParameters();
            p.setEndpointIdentificationAlgorithm("HTTPS");
            client.setSSLParameters(p);
            client.connect(new InetSocketAddress(c.host, c.port));
            final SSLSocket server = (SSLSocket) c.serverSocket.accept();
            Future<Void> future = runAsync(() -> {
                server.startHandshake();
                assertNotNull(server.getSession());
                try {
                    server.getSession().getPeerCertificates();
                    fail();
                } catch (SSLPeerUnverifiedException expected) {
                    // Ignored.
                }
                Certificate[] localCertificates = server.getSession().getLocalCertificates();
                assertNotNull(localCertificates);
                TestKeyStore.assertChainLength(localCertificates);
                assertNotNull(localCertificates[0]);
                TestSSLContext
                        .assertCertificateInKeyStore(localCertificates[0], c.serverKeyStore);
                return null;
            });
            client.startHandshake();
            assertNotNull(client.getSession());
            assertNull(client.getSession().getLocalCertificates());
            Certificate[] peerCertificates = client.getSession().getPeerCertificates();
            assertNotNull(peerCertificates);
            TestKeyStore.assertChainLength(peerCertificates);
            assertNotNull(peerCertificates[0]);
            TestSSLContext.assertCertificateInKeyStore(peerCertificates[0], c.serverKeyStore);
            future.get();
            client.close();
            server.close();
            c.close();
        } finally {
            HttpsURLConnection.setDefaultHostnameVerifier(oldDefault);
        }
    }

    @Test
    public void test_SSLSocket_endpointIdentification_Failure() throws Exception {
        TestUtils.assumeSetEndpointIdentificationAlgorithmAvailable();
        // The default hostname verifier on OpenJDK just rejects all hostnames,
        // which is not helpful, so replace with a basic functional one.
        HostnameVerifier oldDefault = HttpsURLConnection.getDefaultHostnameVerifier();
        HttpsURLConnection.setDefaultHostnameVerifier(new TestHostnameVerifier());
        try {
            final TestSSLContext c = new TestSSLContext.Builder()
                    .clientProtocol(clientVersion)
                    .serverProtocol(serverVersion)
                    .build();
            SSLSocket client = (SSLSocket) c.clientContext.getSocketFactory().createSocket();
            SSLParameters p = client.getSSLParameters();
            p.setEndpointIdentificationAlgorithm("HTTPS");
            client.setSSLParameters(p);
            client.connect(c.getLoopbackAsHostname("unmatched.example.com", c.port));
            final SSLSocket server = (SSLSocket) c.serverSocket.accept();
            Future<Void> future = runAsync(() -> {
                try {
                    server.startHandshake();
                    fail("Should receive SSLHandshakeException as server");
                } catch (SSLHandshakeException expected) {
                    // Ignored.
                }
                return null;
            });
            try {
                client.startHandshake();
                fail("Should throw when hostname does not match expected");
            } catch (SSLHandshakeException expected) {
                // Ignored.
            } finally {
                try {
                    future.get();
                } finally {
                    client.close();
                    server.close();
                    c.close();
                }
            }
        } finally {
            HttpsURLConnection.setDefaultHostnameVerifier(oldDefault);
        }
    }

    @Test
    @Ignore("Broken test: See b/408399060")
    public void test_SSLSocket_setSoWriteTimeout() throws Exception {
        // Only run this test on Linux since it relies on non-posix methods.
        assumeTrue("Test only runs on Linux. Current OS: " + osName(), isLinux());

        // In jb-mr2 it was found that we need to also set SO_RCVBUF
        // to a minimal size or the write would not block.
        final int receiveBufferSize = 128;
        TestSSLContext c = TestSSLContext.newBuilder()
                .serverReceiveBufferSize(receiveBufferSize)
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();

        final SSLSocket client =
                (SSLSocket) c.clientContext.getSocketFactory().createSocket(c.host, c.port);

        // Try to make the client SO_SNDBUF size as small as possible
        // (it can default to 512k or even megabytes).  Note that
        // socket(7) says that the kernel will double the request to
        // leave room for its own book keeping and that the minimal
        // value will be 2048. Also note that tcp(7) says the value
        // needs to be set before connect(2).
        int sendBufferSize = 1024;
        client.setSendBufferSize(sendBufferSize);
        sendBufferSize = client.getSendBufferSize();

        // Start the handshake.
        final SSLSocket server = (SSLSocket) c.serverSocket.accept();
        Future<Void> future = runAsync(() -> {
            client.startHandshake();
            return null;
        });
        server.startHandshake();

        assertTrue(isConscryptSocket(client));
        // The concrete class that Conscrypt returns has methods on it that have no
        // equivalent on the public API (like setSoWriteTimeout), so users have
        // previously used reflection to access those otherwise-inaccessible methods
        // on that class.  The concrete class used to be named OpenSSLSocketImpl, so
        // check that OpenSSLSocketImpl is still in the class hierarchy so applications
        // that rely on getting that class back still work.
        Class<?> superClass = client.getClass();
        do {
            superClass = superClass.getSuperclass();
        } while (superClass != Object.class && !superClass.getName().endsWith("OpenSSLSocketImpl"));
        assertEquals("OpenSSLSocketImpl", superClass.getSimpleName());


        try {
            setWriteTimeout(client, 1);

            // Add extra space to the write to exceed the send buffer
            // size and cause the write to block.
            final int extra = 1;
            client.getOutputStream().write(new byte[sendBufferSize + extra]);
        } finally {
            future.get();
            client.close();
            server.close();
            c.close();
        }
    }

    @Test
    public void test_SSLSocket_reusedNpnSocket() throws Exception {
        byte[] npnProtocols = new byte[] {
                8, 'h', 't', 't', 'p', '/', '1', '.', '1'
        };

        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        SSLSocket client = (SSLSocket) c.clientContext.getSocketFactory().createSocket();

        assertTrue(isConscryptSocket(client));
        Class<?> actualClass = client.getClass();
        Method setNpnProtocols = actualClass.getMethod("setNpnProtocols", byte[].class);

        ExecutorService executor = Executors.newSingleThreadExecutor();

        // First connection with NPN set on client and server
        {
            setNpnProtocols.invoke(client, npnProtocols);
            client.connect(new InetSocketAddress(c.host, c.port));

            final SSLSocket server = (SSLSocket) c.serverSocket.accept();
            assertTrue(isConscryptSocket(server));
            setNpnProtocols.invoke(server, npnProtocols);

            Future<Void> future = executor.submit(() -> {
                server.startHandshake();
                return null;
            });
            client.startHandshake();

            future.get();
            client.close();
            server.close();
        }

        // Second connection with client NPN already set on the SSL context, but
        // without server NPN set.
        {
            SSLServerSocket serverSocket = (SSLServerSocket) c.serverContext
                    .getServerSocketFactory().createServerSocket(0);
            InetAddress host = InetAddress.getLocalHost();
            int port = serverSocket.getLocalPort();

            client = (SSLSocket) c.clientContext.getSocketFactory().createSocket();
            client.connect(new InetSocketAddress(host, port));

            final SSLSocket server = (SSLSocket) serverSocket.accept();

            Future<Void> future = executor.submit(() -> {
                server.startHandshake();
                return null;
            });
            client.startHandshake();

            future.get();
            client.close();
            server.close();
            serverSocket.close();
        }

        c.close();
    }

    // TODO(nmittler): Conscrypt socket read may return -1 instead of SocketException.
    @Test
    public void test_SSLSocket_interrupt_readUnderlyingAndCloseUnderlying() throws Exception {
        test_SSLSocket_interrupt_case(true, true);
    }

    // TODO(nmittler): Conscrypt socket read may return -1 instead of SocketException.
    @Test
    public void test_SSLSocket_interrupt_readUnderlyingAndCloseWrapper() throws Exception {
        test_SSLSocket_interrupt_case(true, false);
    }

    // TODO(nmittler): FD socket gets stuck in read on Windows and OSX.
    @Test
    public void test_SSLSocket_interrupt_readWrapperAndCloseUnderlying() throws Exception {
        test_SSLSocket_interrupt_case(false, true);
    }

    // TODO(nmittler): Conscrypt socket read may return -1 instead of SocketException.
    @Test
    public void test_SSLSocket_interrupt_readWrapperAndCloseWrapper() throws Exception {
        test_SSLSocket_interrupt_case(false, false);
    }

    private void test_SSLSocket_interrupt_case(boolean readUnderlying, boolean closeUnderlying)
            throws Exception {
        final int readingTimeoutMillis = 5000;
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        final Socket underlying = new Socket(c.host, c.port);
        final SSLSocket clientWrapping =
                (SSLSocket) c.clientContext.getSocketFactory().createSocket(
                        underlying, c.host.getHostName(), c.port, true);

        if (isConscryptFdSocket(clientWrapping) && !readUnderlying && closeUnderlying) {
            // TODO(nmittler): FD socket gets stuck in the read on Windows and OSX.
            assumeFalse("Skipping interrupt test on Windows", isWindows());
            assumeFalse("Skipping interrupt test on OSX", isOsx());
        }

        SSLSocket server = (SSLSocket) c.serverSocket.accept();

        // Start the handshake.
        Future<Integer> handshakeFuture = runAsync(() -> {
            clientWrapping.startHandshake();
            return clientWrapping.getInputStream().read();
        });
        server.startHandshake();
        // TLS 1.3 sends some post-handshake management messages, so send a single byte through
        // to process through those messages.
        server.getOutputStream().write(42);
        assertEquals(42, handshakeFuture.get().intValue());

        final Socket toRead = readUnderlying ? underlying : clientWrapping;
        final Socket toClose = closeUnderlying ? underlying : clientWrapping;

        // Schedule the socket to be closed in 1 second.
        Future<Void> future = runAsync(() -> {
            Thread.sleep(1000);
            toClose.close();
            return null;
        });

        // Read from the socket.
        try {
            toRead.setSoTimeout(readingTimeoutMillis);
            int read = toRead.getInputStream().read();
            // In the case of reading the wrapper and closing the underlying socket,
            // there is a race condition between the reading thread being woken and
            // reading the socket again and the closing thread marking the file descriptor
            // as invalid.  If the latter happens first, a SocketException is thrown,
            // but if the former happens first it just looks like the peer closed the
            // connection and a -1 return is acceptable.
            if (read != -1 || readUnderlying || !closeUnderlying) {
                fail();
            }
        } catch (SocketTimeoutException e) {
            throw e;
        } catch (IOException expected) {
            // Expected
        }

        future.get();
        server.close();
        underlying.close();
        server.close();
    }

    /**
     * b/7014266 Test to confirm that an SSLSocket.close() on one
     * thread will interrupt another thread blocked reading on the same
     * socket.
     */
    // TODO(nmittler): Interrupts do not work with the engine-based socket.
    @Test
    public void test_SSLSocket_interrupt_read_withoutAutoClose() throws Exception {
        final int readingTimeoutMillis = 5000;
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        final Socket underlying = new Socket(c.host, c.port);
        final SSLSocket wrapping = (SSLSocket) c.clientContext.getSocketFactory().createSocket(
                underlying, c.host.getHostName(), c.port, false);

        // TODO(nmittler): Interrupts do not work with the engine-based socket.
        assumeFalse(isConscryptEngineSocket(wrapping));

        Future<Void> clientFuture = runAsync(() -> {
            wrapping.startHandshake();
            try {
                wrapping.setSoTimeout(readingTimeoutMillis);
                wrapping.getInputStream().read();
                fail();
            } catch (SocketException expected) {
                // Conscrypt throws an exception complaining that the socket is closed
                // if it's interrupted by a close() in the middle of a read()
                assertTrue(expected.getMessage().contains("closed"));
            }
            return null;
        });
        SSLSocket server = (SSLSocket) c.serverSocket.accept();
        server.startHandshake();

        // Wait for the client to at least be in the "read" method before calling close()
        Thread[] threads = new Thread[1];
        threadGroup.enumerate(threads);
        if (threads[0] != null) {
            boolean clientInRead = false;
            while (!clientInRead) {
                StackTraceElement[] elements = threads[0].getStackTrace();
                for (StackTraceElement element : elements) {
                    if ("read".equals(element.getMethodName())) {
                        // The client might be executing "read" but still not have reached the
                        // point in which it's blocked reading. This is causing flakiness
                        // (b/24367646). Delaying for a fraction of the timeout.
                        Thread.sleep(1000);
                        clientInRead = true;
                        break;
                    }
                }
            }
        }

        wrapping.close();

        clientFuture.get();
        server.close();
    }

    /*
     * Test to confirm that an SSLSocket.close() on one
     * thread will interrupt another thread blocked writing on the same
     * socket.
     *
     * Currently disabled: If the victim thread is not actually blocked in a write
     * call then ConscryptEngineSocket can corrupt the output due to unsynchronized
     * concurrent access to the socket's output stream and cause flakes: b/161347005
     * TODO(prb): Re-enable after underlying bug resolved
     *
     * See also b/147323301 where close() triggered an infinite loop instead.
     */
    @Test
    @Ignore("See comment above")
    public void test_SSLSocket_interrupt_write_withAutoclose() throws Exception {
        final TestSSLContext c = new TestSSLContext.Builder()
            .clientProtocol(clientVersion)
            .serverProtocol(serverVersion)
            .build();
        final Socket underlying = new Socket(c.host, c.port);
        final SSLSocket wrapping = (SSLSocket) c.clientContext.getSocketFactory().createSocket(
            underlying, c.host.getHostName(), c.port, true);
        final byte[] data = new byte[1024 * 64];

        // TODO(b/161347005): Re-enable once engine-based socket interruption works correctly.
        assumeFalse(isConscryptEngineSocket(wrapping));
        Future<Void> clientFuture = runAsync(() -> {
            wrapping.startHandshake();
            try {
                for (int i = 0; i < 64; i++) {
                    wrapping.getOutputStream().write(data);
                }
                // Failure here means that no exception was thrown, so the data buffer is
                // probably too small.
                fail();
            } catch (SocketException expected) {
                assertTrue(expected.getMessage().contains("closed"));
            }
            return null;
        });
        SSLSocket server = (SSLSocket) c.serverSocket.accept();
        server.startHandshake();

        // Read one byte so that both ends are in a fully connected state and data has
        // started to flow, and then close the socket from this thread.
        int unused = server.getInputStream().read();
        wrapping.close();

        clientFuture.get();
        server.close();
    }


    @Test
    public void test_SSLSocket_ClientHello_record_size() throws Exception {
        // This test checks the size of ClientHello of the default SSLSocket. TLS/SSL handshakes
        // with older/unpatched F5/BIG-IP appliances are known to stall and time out when
        // the fragment containing ClientHello is between 256 and 511 (inclusive) bytes long.
        SSLContext sslContext = SSLContext.getInstance(clientVersion);
        sslContext.init(null, null, null);
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        sslSocketFactory = new DelegatingSSLSocketFactory(sslSocketFactory) {
            @Override
            protected SSLSocket configureSocket(SSLSocket socket) {
                // Enable SNI extension on the socket (this is typically enabled by default)
                // to increase the size of ClientHello.
                setHostname(socket);

                // Enable Session Tickets extension on the socket (this is typically enabled
                // by default) to increase the size of ClientHello.
                enableSessionTickets(socket);
                return socket;
            }
        };
        TlsRecord firstReceivedTlsRecord = TlsTester.captureTlsHandshakeFirstTlsRecord(executor, sslSocketFactory);
        assertEquals("TLS record type", TlsProtocols.HANDSHAKE, firstReceivedTlsRecord.type);
        HandshakeMessage handshakeMessage = HandshakeMessage.read(
                new DataInputStream(new ByteArrayInputStream(firstReceivedTlsRecord.fragment)));
        assertEquals(
                "HandshakeMessage type", HandshakeMessage.TYPE_CLIENT_HELLO, handshakeMessage.type);
        int fragmentLength = firstReceivedTlsRecord.fragment.length;
        if ((fragmentLength >= 256) && (fragmentLength <= 511)) {
            fail("Fragment containing ClientHello is of dangerous length: " + fragmentLength
                    + " bytes");
        }
    }

    @Test
    public void test_SSLSocket_ClientHello_SNI() throws Exception {
        ForEachRunner.runNamed(sslSocketFactory -> {
            ClientHello clientHello = TlsTester
                .captureTlsHandshakeClientHello(executor, sslSocketFactory);
            ServerNameHelloExtension sniExtension =
                (ServerNameHelloExtension) clientHello.findExtensionByType(
                    HelloExtension.TYPE_SERVER_NAME);
            assertNotNull(sniExtension);
            assertEquals(
                Collections.singletonList("localhost.localdomain"), sniExtension.hostnames);
        }, getSSLSocketFactoriesToTest());
    }

    @Test
    public void test_SSLSocket_ClientHello_ALPN() throws Exception {
        final String[] protocolList = new String[] { "h2", "http/1.1" };

        ForEachRunner.runNamed(sslSocketFactory -> {
            ClientHello clientHello = TlsTester.captureTlsHandshakeClientHello(executor,
                    new DelegatingSSLSocketFactory(sslSocketFactory) {
                        @Override public SSLSocket configureSocket(SSLSocket socket) {
                            Conscrypt.setApplicationProtocols(socket, protocolList);
                            return socket;
                        }
                    });
            AlpnHelloExtension alpnExtension =
                    (AlpnHelloExtension) clientHello.findExtensionByType(
                            HelloExtension.TYPE_APPLICATION_LAYER_PROTOCOL_NEGOTIATION);
            assertNotNull(alpnExtension);
            assertEquals(Arrays.asList(protocolList), alpnExtension.protocols);
        }, getSSLSocketFactoriesToTest());
    }

    private List<Pair<String, SSLSocketFactory>> getSSLSocketFactoriesToTest()
            throws NoSuchAlgorithmException, KeyManagementException {
        List<Pair<String, SSLSocketFactory>> result =
                new ArrayList<>();
        result.add(Pair.of("default", (SSLSocketFactory) SSLSocketFactory.getDefault()));
        for (String sslContextProtocol : StandardNames.SSL_CONTEXT_PROTOCOLS) {
            SSLContext sslContext = SSLContext.getInstance(sslContextProtocol);
            if (StandardNames.SSL_CONTEXT_PROTOCOLS_DEFAULT.equals(sslContextProtocol)) {
                continue;
            }
            sslContext.init(null, null, null);
            result.add(Pair.of("SSLContext(\"" + sslContext.getProtocol() + "\")",
                    sslContext.getSocketFactory()));
        }
        return result;
    }

    // http://b/18428603
    @Test
    public void test_SSLSocket_getPortWithSNI() throws Exception {
        TestSSLContext context = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        try (SSLSocket client
                     = (SSLSocket) context.clientContext.getSocketFactory().createSocket()) {
            client.connect(new InetSocketAddress(context.host, context.port));
            setHostname(client);
            assertTrue(client.getPort() > 0);
        } finally {
            context.close();
        }
    }

    @Test
    public void test_SSLSocket_SNIHostName() throws Exception {
        TestUtils.assumeSNIHostnameAvailable();
        final TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        final SSLSocket server;
        try (SSLSocket client = (SSLSocket) c.clientContext.getSocketFactory().createSocket()) {
            SSLParameters clientParams = client.getSSLParameters();
            clientParams.setServerNames(
                    Collections.singletonList(new SNIHostName("www.example.com")));
            client.setSSLParameters(clientParams);
            SSLParameters serverParams = c.serverSocket.getSSLParameters();
            serverParams.setSNIMatchers(Collections.singletonList(
                    SNIHostName.createSNIMatcher("www\\.example\\.com")));
            c.serverSocket.setSSLParameters(serverParams);
            client.connect(new InetSocketAddress(c.host, c.port));
            server = (SSLSocket) c.serverSocket.accept();
            @SuppressWarnings("unused")
            Future<?> future = runAsync(() -> {
                client.startHandshake();
                return null;
            });
            server.startHandshake();
        }
        SSLSession serverSession = server.getSession();
        assertTrue(serverSession instanceof ExtendedSSLSession);
        ExtendedSSLSession extendedServerSession = (ExtendedSSLSession) serverSession;
        List<SNIServerName> requestedNames = extendedServerSession.getRequestedServerNames();
        assertNotNull(requestedNames);
        assertEquals(1, requestedNames.size());
        SNIServerName serverName = requestedNames.get(0);
        assertEquals(StandardConstants.SNI_HOST_NAME, serverName.getType());
        assertTrue(serverName instanceof SNIHostName);
        SNIHostName serverHostName = (SNIHostName) serverName;
        assertEquals("www.example.com", serverHostName.getAsciiName());
        server.close();
    }

    @Test
    public void test_SSLSocket_ClientGetsAlertDuringHandshake_HasGoodExceptionMessage()
            throws Exception {
        TestSSLContext context = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        final ServerSocket listener = ServerSocketFactory.getDefault().createServerSocket(0);
        final SSLSocket client = (SSLSocket) context.clientContext.getSocketFactory().createSocket(
                context.host, listener.getLocalPort());
        final Socket server = listener.accept();
        Future<Void> c = runAsync(() -> {
            try {
                client.startHandshake();
                fail("Should receive handshake exception");
            } catch (SSLHandshakeException expected) {
                assertFalse(expected.getMessage().contains("SSL_ERROR_ZERO_RETURN"));
                assertFalse(expected.getMessage().contains("You should never see this."));
            }
            return null;
        });
        Future<Void> s = runAsync(() -> {
            // Wait until the client sends something.
            byte[] scratch = new byte[8192];
            @SuppressWarnings("unused")
            int bytesRead = server.getInputStream().read(scratch);
            // Write a bogus TLS alert:
            // TLSv1.2 Record Layer: Alert (Level: Warning, Description: Protocol Version)
            server.getOutputStream()
                .write(new byte[]{0x15, 0x03, 0x03, 0x00, 0x02, 0x01, 0x46});
            // TLSv1.2 Record Layer: Alert (Level: Warning, Description: Close Notify)
            server.getOutputStream()
                .write(new byte[]{0x15, 0x03, 0x03, 0x00, 0x02, 0x01, 0x00});
            return null;
        });
        c.get(5, TimeUnit.SECONDS);
        s.get(5, TimeUnit.SECONDS);
        client.close();
        server.close();
        listener.close();
        context.close();
    }

    @Test
    public void test_SSLSocket_ServerGetsAlertDuringHandshake_HasGoodExceptionMessage()
            throws Exception {
        TestSSLContext context = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        final Socket client = SocketFactory.getDefault().createSocket(context.host, context.port);
        final SSLSocket server = (SSLSocket) context.serverSocket.accept();
        Future<Void> s = runAsync(() -> {
            try {
                server.startHandshake();
                fail("Should receive handshake exception");
            } catch (SSLHandshakeException expected) {
                assertFalse(expected.getMessage().contains("SSL_ERROR_ZERO_RETURN"));
                assertFalse(expected.getMessage().contains("You should never see this."));
            }
            return null;
        });
        Future<Void> c = runAsync(() -> {
            // Send bogus ClientHello:
            // TLSv1.2 Record Layer: Handshake Protocol: Client Hello
            client.getOutputStream().write(new byte[]{
                (byte) 0x16, (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0xb9,
                (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0xb5, (byte) 0x03,
                (byte) 0x03, (byte) 0x5a, (byte) 0x31, (byte) 0xba, (byte) 0x44,
                (byte) 0x24, (byte) 0xfd, (byte) 0xf0, (byte) 0x56, (byte) 0x46,
                (byte) 0xea, (byte) 0xee, (byte) 0x1c, (byte) 0x62, (byte) 0x8f,
                (byte) 0x18, (byte) 0x04, (byte) 0xbd, (byte) 0x1c, (byte) 0xbc,
                (byte) 0xbf, (byte) 0x6d, (byte) 0x84, (byte) 0x12, (byte) 0xe9,
                (byte) 0x94, (byte) 0xf5, (byte) 0x1c, (byte) 0x15, (byte) 0x3e,
                (byte) 0x79, (byte) 0x01, (byte) 0xe2, (byte) 0x00, (byte) 0x00,
                (byte) 0x28, (byte) 0xc0, (byte) 0x2b, (byte) 0xc0, (byte) 0x2c,
                (byte) 0xc0, (byte) 0x2f, (byte) 0xc0, (byte) 0x30, (byte) 0x00,
                (byte) 0x9e, (byte) 0x00, (byte) 0x9f, (byte) 0xc0, (byte) 0x09,
                (byte) 0xc0, (byte) 0x0a, (byte) 0xc0, (byte) 0x13, (byte) 0xc0,
                (byte) 0x14, (byte) 0x00, (byte) 0x33, (byte) 0x00, (byte) 0x39,
                (byte) 0xc0, (byte) 0x07, (byte) 0xc0, (byte) 0x11, (byte) 0x00,
                (byte) 0x9c, (byte) 0x00, (byte) 0x9d, (byte) 0x00, (byte) 0x2f,
                (byte) 0x00, (byte) 0x35, (byte) 0x00, (byte) 0x05, (byte) 0x00,
                (byte) 0xff, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x64,
                (byte) 0x00, (byte) 0x0b, (byte) 0x00, (byte) 0x04, (byte) 0x03,
                (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x00, (byte) 0x0a,
                (byte) 0x00, (byte) 0x34, (byte) 0x00, (byte) 0x32, (byte) 0x00,
                (byte) 0x0e, (byte) 0x00, (byte) 0x0d, (byte) 0x00, (byte) 0x19,
                (byte) 0x00, (byte) 0x0b, (byte) 0x00, (byte) 0x0c, (byte) 0x00,
                (byte) 0x18, (byte) 0x00, (byte) 0x09, (byte) 0x00, (byte) 0x0a,
                (byte) 0x00, (byte) 0x16, (byte) 0x00, (byte) 0x17, (byte) 0x00,
                (byte) 0x08, (byte) 0x00, (byte) 0x06, (byte) 0x00, (byte) 0x07,
                (byte) 0x00, (byte) 0x14, (byte) 0x00, (byte) 0x15, (byte) 0x00,
                (byte) 0x04, (byte) 0x00, (byte) 0x05, (byte) 0x00, (byte) 0x12,
                (byte) 0x00, (byte) 0x13, (byte) 0x00, (byte) 0x01, (byte) 0x00,
                (byte) 0x02, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x0f,
                (byte) 0x00, (byte) 0x10, (byte) 0x00, (byte) 0x11, (byte) 0x00,
                (byte) 0x0d, (byte) 0x00, (byte) 0x20, (byte) 0x00, (byte) 0x1e,
                (byte) 0x06, (byte) 0x01, (byte) 0x06, (byte) 0x02, (byte) 0x06,
                (byte) 0x03, (byte) 0x05, (byte) 0x01, (byte) 0x05, (byte) 0x02,
                (byte) 0x05, (byte) 0x03, (byte) 0x04, (byte) 0x01, (byte) 0x04,
                (byte) 0x02, (byte) 0x04, (byte) 0x03, (byte) 0x03, (byte) 0x01,
                (byte) 0x03, (byte) 0x02, (byte) 0x03, (byte) 0x03, (byte) 0x02,
                (byte) 0x01, (byte) 0x02, (byte) 0x02, (byte) 0x02, (byte) 0x03,
            });
            // Wait until the server sends something.
            byte[] scratch = new byte[8192];
            @SuppressWarnings("unused")
            int bytesRead = client.getInputStream().read(scratch);
            // Write a bogus TLS alert:
            // TLSv1.2 Record Layer: Alert (Level: Warning, Description:
            // Protocol Version)
            client.getOutputStream()
                .write(new byte[]{0x15, 0x03, 0x03, 0x00, 0x02, 0x01, 0x46});
            // TLSv1.2 Record Layer: Alert (Level: Warning, Description:
            // Close Notify)
            client.getOutputStream()
                .write(new byte[]{0x15, 0x03, 0x03, 0x00, 0x02, 0x01, 0x00});
            return null;
        });
        c.get(5, TimeUnit.SECONDS);
        s.get(5, TimeUnit.SECONDS);
        client.close();
        server.close();
        context.close();
    }

    @Test
    public void test_SSLSocket_TLSv1Supported() throws Exception {
        assumeTrue(isTlsV1Supported());
        TestSSLContext context = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        try (SSLSocket client = (SSLSocket) context.clientContext.getSocketFactory().createSocket())
        {
            client.setEnabledProtocols(new String[]{"TLSv1", "TLSv1.1"});
            assertEquals(2, client.getEnabledProtocols().length);
        }
    }

    @Test
    public void test_TLSv1Unsupported_notEnabled() {
        assumeTrue(!isTlsV1Supported());
        assertTrue(isTlsV1Deprecated());
    }

    // Under some circumstances, the file descriptor socket may get finalized but still
    // be reused by the JDK's built-in HTTP connection reuse code.  Ensure that a
    // SocketException is thrown if that happens.
    @Test
    public void test_SSLSocket_finalizeThrowsProperException() throws Exception {
        TestSSLContext context = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        TestSSLSocketPair test = TestSSLSocketPair.create(context).connect();
        try {
            if (isConscryptFdSocket(test.client)) {
                // The finalize method might be declared on a superclass rather than this
                // class.
                Method method = null;
                Class<?> clazz = test.client.getClass();
                while (clazz != null) {
                    try {
                        method = clazz.getDeclaredMethod("finalize");
                        break;
                    } catch (NoSuchMethodException e) {
                        // Try the superclass
                    }
                    clazz = clazz.getSuperclass();
                }
                assertNotNull(method);
                method.setAccessible(true);
                method.invoke(test.client);
                try {
                    test.client.getOutputStream().write(new byte[] { 0x01 });
                    fail("The socket shouldn't work after being finalized");
                } catch (SocketException expected) {
                    // Expected
                }
            }
        } finally {
            test.close();
        }
    }

    @Test
    public void test_SSLSocket_TlsUnique() throws Exception {
        // tls_unique isn't supported in TLS 1.3
        assumeTlsV1_2Connection();
        TestSSLContext context = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        TestSSLSocketPair pair = TestSSLSocketPair.create(context);
        try {
            assertNull(Conscrypt.getTlsUnique(pair.client));
            assertNull(Conscrypt.getTlsUnique(pair.server));

            pair.connect();

            byte[] clientTlsUnique = Conscrypt.getTlsUnique(pair.client);
            byte[] serverTlsUnique = Conscrypt.getTlsUnique(pair.server);
            assertNotNull(clientTlsUnique);
            assertNotNull(serverTlsUnique);
            assertArrayEquals(clientTlsUnique, serverTlsUnique);
        } finally {
            pair.close();
        }
    }

    // Tests that all cipher suites have a 12-byte tls-unique channel binding value.  If this
    // test fails, that means some cipher suite has been added that uses a customized verify_data
    // length and we need to update MAX_TLS_UNIQUE_LENGTH in native_crypto.cc to account for that.
    @Test
    public void test_SSLSocket_TlsUniqueLength() throws Exception {
        // tls_unique isn't supported in TLS 1.3
        assumeTlsV1_2Connection();
        // note the rare usage of non-RSA keys
        TestKeyStore testKeyStore = new TestKeyStore.Builder()
                .keyAlgorithms("RSA", "DSA", "EC", "EC_RSA")
                .aliasPrefix("rsa-dsa-ec")
                .ca(true)
                .build();
        KeyManager pskKeyManager =
                PSKKeyManagerProxy.getConscryptPSKKeyManager(new PSKKeyManagerProxy() {
                    @Override
                    protected SecretKey getKey(
                            String identityHint, String identity, Socket socket) {
                        return newKey();
                    }

                    @Override
                    protected SecretKey getKey(
                            String identityHint, String identity, SSLEngine engine) {
                        return newKey();
                    }

                    private SecretKey newKey() {
                        return new SecretKeySpec("Just an arbitrary key".getBytes(UTF_8), "RAW");
                    }
                });
        TestSSLContext c = TestSSLContext.newBuilder()
                .client(testKeyStore)
                .server(testKeyStore)
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .additionalClientKeyManagers(new KeyManager[] {pskKeyManager})
                .additionalServerKeyManagers(new KeyManager[] {pskKeyManager})
                .build();
        for (String cipherSuite : c.clientContext.getSocketFactory().getSupportedCipherSuites()) {
            if (cipherSuite.equals(StandardNames.CIPHER_SUITE_FALLBACK)
                    || cipherSuite.equals(StandardNames.CIPHER_SUITE_SECURE_RENEGOTIATION)) {
                continue;
            }
            /*
             * tls_unique only works on 1.2, so skip TLS 1.3 cipher suites.
             */
            if (StandardNames.CIPHER_SUITES_TLS13.contains(cipherSuite)) {
                continue;
            }
            TestSSLSocketPair pair = TestSSLSocketPair.create(c);
            try {
                String[] cipherSuites =
                        new String[] {cipherSuite, StandardNames.CIPHER_SUITE_SECURE_RENEGOTIATION};
                pair.connect(cipherSuites, cipherSuites);

                assertEquals(cipherSuite, pair.client.getSession().getCipherSuite());

                byte[] clientTlsUnique = Conscrypt.getTlsUnique(pair.client);
                byte[] serverTlsUnique = Conscrypt.getTlsUnique(pair.server);
                assertNotNull(clientTlsUnique);
                assertNotNull(serverTlsUnique);
                assertArrayEquals(clientTlsUnique, serverTlsUnique);
                assertEquals(12, clientTlsUnique.length);
            } catch (Exception e) {
                throw new AssertionError("Cipher suite is " + cipherSuite, e);
            } finally {
                pair.client.close();
                pair.server.close();
            }
        }
    }

    @Test
    public void test_SSLSocket_EKM() throws Exception {
        TestSSLContext context = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        TestSSLSocketPair pair = TestSSLSocketPair.create(context);
        try {
            // No EKM values available before handshaking
            assertNull(Conscrypt.exportKeyingMaterial(pair.client, "FOO", null, 20));
            assertNull(Conscrypt.exportKeyingMaterial(pair.server, "FOO", null, 20));

            pair.connect();

            byte[] clientEkm = Conscrypt.exportKeyingMaterial(pair.client, "FOO", null, 20);
            byte[] serverEkm = Conscrypt.exportKeyingMaterial(pair.server, "FOO", null, 20);
            assertNotNull(clientEkm);
            assertNotNull(serverEkm);
            assertEquals(20, clientEkm.length);
            assertEquals(20, serverEkm.length);
            assertArrayEquals(clientEkm, serverEkm);

            byte[] clientContextEkm = Conscrypt.exportKeyingMaterial(
                    pair.client, "FOO", new byte[0], 20);
            byte[] serverContextEkm = Conscrypt.exportKeyingMaterial(
                    pair.server, "FOO", new byte[0], 20);
            assertNotNull(clientContextEkm);
            assertNotNull(serverContextEkm);
            assertEquals(20, clientContextEkm.length);
            assertEquals(20, serverContextEkm.length);
            assertArrayEquals(clientContextEkm, serverContextEkm);

            // In TLS 1.2, an empty context and a null context are different (RFC 5705, section 4),
            // but in TLS 1.3 they are the same (RFC 8446, section 7.5).
            if ("TLSv1.2".equals(negotiatedVersion())) {
                assertFalse(Arrays.equals(clientEkm, clientContextEkm));
            } else {
                assertArrayEquals(clientEkm, clientContextEkm);
            }
        } finally {
            pair.close();
        }
    }

    // Tests that a socket will close cleanly even if it fails to create due to an
    // internal IOException
    @Test
    public void test_SSLSocket_CloseCleanlyOnConstructorFailure() throws Exception {
        TestSSLContext c = new TestSSLContext.Builder()
                .clientProtocol(clientVersion)
                .serverProtocol(serverVersion)
                .build();
        try {
            c.clientContext.getSocketFactory().createSocket(c.host, 1);
            fail();
        } catch (ConnectException ignored) {
            // Ignored.
        }
    }

    private static void setWriteTimeout(Object socket, int timeout) {
        Exception ex = null;
        try {
            Method method = socket.getClass().getMethod("setSoWriteTimeout", int.class);
            method.setAccessible(true);
            method.invoke(socket, timeout);
        } catch (Exception e) {
            ex = e;
        }
        // Engine-based socket currently has the method but throws UnsupportedOperationException.
        assumeNoException("Client socket does not support setting write timeout", ex);
    }

    private static void setHostname(SSLSocket socket) {
        try {
            Method method = socket.getClass().getMethod("setHostname", String.class);
            method.setAccessible(true);
            method.invoke(socket, "sslsockettest.androidcts.google.com");
        } catch (NoSuchMethodException ignored) {
            // Ignored.
        } catch (Exception e) {
            throw new RuntimeException("Failed to enable SNI", e);
        }
    }

    private static void enableSessionTickets(SSLSocket socket) {
        try {
            Method method =
                    socket.getClass().getMethod("setUseSessionTickets", boolean.class);
            method.setAccessible(true);
            method.invoke(socket, true);
        } catch (NoSuchMethodException ignored) {
            // Ignored.
        } catch (Exception e) {
            throw new RuntimeException("Failed to enable Session Tickets", e);
        }
    }

    private static boolean isConscryptSocket(SSLSocket socket) {
        return isConscryptFdSocket(socket) || isConscryptEngineSocket(socket);
    }

    private static boolean isConscryptFdSocket(SSLSocket socket) {
        Class<?> clazz = socket.getClass();
        while (clazz != Object.class && !"ConscryptFileDescriptorSocket".equals(clazz.getSimpleName())) {
            clazz = clazz.getSuperclass();
        }
        return "ConscryptFileDescriptorSocket".equals(clazz.getSimpleName());
    }

    private static boolean isConscryptEngineSocket(SSLSocket socket) {
        Class<?> clazz = socket.getClass();
        while (clazz != Object.class && !"ConscryptEngineSocket".equals(clazz.getSimpleName())) {
            clazz = clazz.getSuperclass();
        }
        return "ConscryptEngineSocket".equals(clazz.getSimpleName());
    }

    private <T> Future<T> runAsync(Callable<T> callable) {
        return executor.submit(callable);
    }

    private static SSLContext defaultInit(SSLContext context) throws KeyManagementException {
        context.init(null, null, null);
        return context;
    }

    // Assumes that the negotiated connection will be TLS 1.2
    private void assumeTlsV1_2Connection() {
        assumeTrue("TLSv1.2".equals(negotiatedVersion()));
    }

    /**
     * Returns the version that a connection between {@code clientVersion} and
     * {@code serverVersion} should produce.
     */
    private String negotiatedVersion() {
        if (clientVersion.equals("TLSv1.3") && serverVersion.equals("TLSv1.3")) {
            return "TLSv1.3";
        } else {
            return "TLSv1.2";
        }
    }
}
