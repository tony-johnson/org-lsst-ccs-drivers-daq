package org.lsst.ccs.daq.ims.example;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.lsst.ccs.command.annotations.Argument;
import org.lsst.ccs.command.annotations.Command;
import org.lsst.ccs.daq.ims.DAQException;
import org.lsst.ccs.daq.ims.Guider;
import org.lsst.ccs.daq.ims.Guider.ROICommon;
import org.lsst.ccs.daq.ims.Guider.ROILocation;
import org.lsst.ccs.daq.ims.Store;
import org.lsst.ccs.daq.ims.Version;
import org.lsst.ccs.utilities.location.Location;
import org.lsst.ccs.utilities.location.LocationSet;

/**
 *
 * @author tonyj
 */
public class GuiderTool {

    private static final Logger LOG = Logger.getLogger(GuiderTool.class.getName());

    private Store store;
    private Guider guider;

    public GuiderTool() {
    }

    @Command(name = "connect", description = "Connect to a DAQ guider")
    public void connect(
            @Argument(name = "partition", description = "Partition name") String partition) throws DAQException {

        if (store != null) {
            store.close();
        }
        store = new Store(partition);
        guider = store.getGuider();
    }

    @Command(name = "close", description = "Close DAQ store")
    public void close() throws DAQException {
        if (store != null) {
            store.close();
            store = null;
        }
    }

    @Command(name = "stop", description = "Stop the guider")
    public void stop() throws DAQException {
        checkStore();
        guider.stop();
    }

    @Command(name = "pause", description = "Pause the guider")
    public void pause() throws DAQException {
        checkStore();
        guider.pause();
    }

    @Command(name = "resume", description = "Resume the guider")
    public void resume() throws DAQException {
        checkStore();
        guider.resume();
    }

    @Command(name = "sleep", description = "Sleep the guider")
    public void sleep() throws DAQException {
        checkStore();
        guider.sleep();
    }
    
    @Command(name = "wake", description = "Wake the guider")
    public void wake() throws DAQException {
        checkStore();
        guider.wake();
    }
    
    
    @Command(name = "config", description = "get the guider config")
    public Guider.GuiderConfig config() throws DAQException {
        checkStore();
        return guider.config();
    }
    
    @Command(name = "start", description = "Start the guider")
    public void start() throws DAQException {
        checkStore();
        List<ROILocation> locations = new ArrayList<>();
        Location R00 = Location.of("R00/RebG");
        locations.add(new ROILocation(R00, 0, 4, 100, 100));
        ROICommon roi = new ROICommon(50, 50, 100, 1);
        guider.start(roi, locations);
    }

    @Command(name = "listen", description = "Listen for guider events")
    public void listen() throws DAQException {
        checkStore();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Location R00 = Location.of("R00/RebG");;
                    guider.listen(R00, 0);
                } catch (DAQException x) {
                    LOG.log(Level.SEVERE, "Error in listener", x);
                }
            }
        });
        t.start();
    }

    @Command(name = "version", description = "Get version info")
    public Version version() throws DAQException {
        return Store.getClientVersion();
    }

    @Command(name = "locations", description = "List configured locations")
    public LocationSet locations() throws DAQException {
        checkStore();
        return store.getConfiguredSources();
    }

    private void checkStore() {
        if (store == null) {
            throw new RuntimeException("Please connect to store first");
        }
    }
}