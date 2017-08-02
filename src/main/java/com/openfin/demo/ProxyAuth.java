package com.openfin.demo;

import com.openfin.desktop.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Example of authenticating proxy servers with OpenFin API
 *
 *  The following runtime options are supported:
 *
 *  -Dcom.openfin.com.openfin.demo.proxy.username: username
 *  -Dcom.openfin.com.openfin.demo.proxy.password
 *  -Dcom.openfin.com.openfin.demo.proxy.location  ("server:port")
 *  -Dcom.openfin.com.openfin.demo.runtime.version
 *
 */

public class ProxyAuth implements DesktopStateListener, EventListener {
    private final static Logger logger = LoggerFactory.getLogger(ProxyAuth.class.getName());

    private RuntimeConfiguration runtimeConfiguration;
    private DesktopConnection desktopConnection;
    private String proxyUsername;
    private String proxyPassword;
    private Application application;  // OpenFin app
    private boolean shouldCancelAuth = false;

    private static Integer sema = new Integer(0);   // semaphore to wait for Runtime to exit

    private static String HTML5_APP_UUID = "HTML5 APP";  // UUID of html5 app launched by this app once connected to Runtime

    public ProxyAuth() throws Exception {
        this.runtimeConfiguration = new RuntimeConfiguration();
        String version = java.lang.System.getProperty("com.openfin.demo.runtime.version");
        if (version == null) {
            throw new IllegalArgumentException("Missing com.openfin.demo.runtime.version");
        }
        this.runtimeConfiguration.setRuntimeVersion(version);
        this.proxyUsername = java.lang.System.getProperty("com.openfin.demo.proxy.username");
        this.proxyPassword = java.lang.System.getProperty("com.openfin.demo.proxy.password");
        String proxy = java.lang.System.getProperty("com.openfin.demo.proxy.location");
        if (proxy != null) {
            this.runtimeConfiguration.setAdditionalRuntimeArguments(String.format("--v=1 --no-sandbox --proxy-server=%s", proxy));  // --v=1 enables verbose logging
        } else {
            this.runtimeConfiguration.setAdditionalRuntimeArguments(String.format("--v=1 --no-sandbox"));  // --v=1 enables verbose logging
        }
    }

    public void start() throws Exception {
        // start Runtime first.  onReady is called when this class is connected to Runtime
        this.desktopConnection = new DesktopConnection("ProxyAuthProvider");
        this.desktopConnection.connect(this.runtimeConfiguration, this, 30);
    }

    public void startHTML5App() throws Exception {
        // Launch HTML5 apps after connected
        // This example launch Hello OpenFin com.openfin.demo app
        ApplicationOptions options = new ApplicationOptions(HTML5_APP_UUID, HTML5_APP_UUID, "http://demoappdirectory.openf.in/desktop/config/apps/OpenFin/HelloOpenFin/index.html");
        // configure window stuff
        WindowOptions windowOptions = new WindowOptions();
        windowOptions.setAutoShow(true);
        windowOptions.setDefaultHeight(525);
        windowOptions.setDefaultWidth(395);
        windowOptions.setDefaultTop(200);
        windowOptions.setDefaultLeft(200);
        windowOptions.setFrame(false);
        windowOptions.setSaveWindowState(false);
        options.setMainWindowOptions(windowOptions);
        this.application = new Application(options, this.desktopConnection, new AckListener() {
            @Override
            public void onSuccess(Ack ack) {
                if (application != null) {
                    try {
                        // set up listener for auth-requested event
                        application.addEventListener("window-auth-requested", ProxyAuth.this, null);
                        // set up listener for closed event of the app
                        application.addEventListener("closed", ProxyAuth.this, null);
                        application.run();
                    } catch (Exception ex) {
                        logger.error("Error running application", ex);
                        exitRuntime();
                    }
                }
            }
            @Override
            public void onError(Ack ack) {
                logger.error(String.format("Error creating application %s", ack.getReason()));
                exitRuntime();
            }
        });
    }

    private void exitRuntime() {
        try {
            desktopConnection.exit();
        } catch (Exception ex) {
            logger.error("Error exiting Runtime", ex);
        }
    }
    // override methods for DesktopStateListener
    @Override
    public void onReady() {
        logger.info("Connected to Runtime");
        try {
            startHTML5App();
        } catch (Exception ex) {
            logger.error("Error creating HTML5 apps", ex);
        }
    }
    @Override
    public void onClose() {
        logger.info("Connection to Runtime is closed");
        synchronized (sema) {
            sema.notify();
        }
    }
    @Override
    public void onError(String reason) {
        logger.error(String.format("Connection to Runtime is closed %s", reason));
    }
    @Override
    public void onMessage(String message) {
    }
    @Override
    public void onOutgoingMessage(String message) {
    }

    // override method of EventListener
    @Override
    public void eventReceived(ActionEvent actionEvent) {
        logger.info(actionEvent.getEventObject().toString());
        if (actionEvent.getType().equals("window-auth-requested")) {
            if (!shouldCancelAuth) {
                // actionEvent.getEventObject() should looks like:
                // {"name":"HTML5 APP","topic":"window","type":"auth-requested","uuid":"HTML5 APP","authInfo":{"scheme":"basic","port":8888,"host":"proxy.mycompany.com","realm":"Proxy Name","isProxy":true}}
                // pick the correct window based on uuid and name in EventObject
                String uuid = actionEvent.getEventObject().getString("uuid");
                String name = actionEvent.getEventObject().getString("name");
                Window authWindow = Window.wrap(uuid, name, desktopConnection);
                authWindow.authenticate(proxyUsername, proxyPassword, shouldCancelAuth, new AckListener() {
                    @Override
                    public void onSuccess(Ack ack) {
                        logger.info("proxy authentication worked");
                    }

                    @Override
                    public void onError(Ack ack) {
                        logger.info("proxy authentication failed");
                    }
                });
                shouldCancelAuth = true;   // just try auth once.
            } else {
                logger.info("Proxy auth failed.  shutting down Runtime");
                exitRuntime();
            }
        }
        else if (actionEvent.getType().equals("closed")) {
            // OpenFin app is closed.  Exit Runtime
            exitRuntime();
        }
    }

    public static void main(String[] args) throws Exception {
        ProxyAuth proxyAuth = new ProxyAuth();
        proxyAuth.start();

        synchronized (sema) {
            sema.wait();
        }
    }

}
