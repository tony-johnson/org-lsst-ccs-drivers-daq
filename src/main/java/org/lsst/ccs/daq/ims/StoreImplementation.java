package org.lsst.ccs.daq.ims;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import org.lsst.ccs.utilities.location.Location;

/**
 * An interface implemented by both the native and simulated 
 * @author tonyj
 */
interface StoreImplementation {
   long attachStore(String partition) throws DAQException;
   
   void detachStore(long store) throws DAQException;

   long capacity(long store) throws DAQException;

   long remaining(long store) throws DAQException;

   void listFolders(long store, List<String> result) throws DAQException;

   int insertFolder(long store, String folderName) throws DAQException;

   int removeFolder(long store, String folderName) throws DAQException;

   boolean findFolder(long store, String folderName) throws DAQException;

   void listImages(long store, String folderName, List<ImageMetaData> result) throws DAQException;

   int moveImageToFolder(long store, long id, String folderName) throws DAQException;

   int deleteImage(long store, long id) throws DAQException;

   SourceMetaData findSource(long store, long id, int location) throws DAQException;

   ImageMetaData addImageToFolder(long store, String imageName, String folderName, String annotation, int opcode, BitSet elements) throws DAQException;

   ImageMetaData findImage(long store, String imageName, String folderName) throws DAQException;

   long openSourceChannel(long store, long id, int index, boolean write) throws DAQException;

   SourceMetaData addSourceToImage(long store, long id, int index, byte type, String platform, int[] registerValues) throws DAQException;

   int waitForImage(long store, int imageTimeoutMicros, int sourceTimeoutMicros) throws DAQException;

   String decodeException(int rc);

   BitSet getConfiguredSources(long store) throws DAQException;

   Version getClientVersion() throws DAQException;

   ImageMetaData triggerImage(long store, ImageMetaData meta, Map<Location.LocationType, int[]> registerLists) throws DAQException;

   long startSequencer(long store, int opcode) throws DAQException;

}
