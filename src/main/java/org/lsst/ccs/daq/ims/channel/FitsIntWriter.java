package org.lsst.ccs.daq.ims.channel;

import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nom.tam.fits.FitsException;
import nom.tam.fits.FitsFactory;
import org.lsst.ccs.daq.ims.DAQException;
import org.lsst.ccs.daq.ims.Source;
import org.lsst.ccs.daq.ims.SourceMetaData;
import org.lsst.ccs.imagenaming.ImageName;
import org.lsst.ccs.utilities.ccd.CCD;
import org.lsst.ccs.utilities.ccd.Reb;
import org.lsst.ccs.utilities.image.FitsFileWriter;
import org.lsst.ccs.utilities.image.FitsHeaderMetadataProvider;
import org.lsst.ccs.utilities.image.FitsHeadersSpecificationsBuilder;
import org.lsst.ccs.utilities.image.ImageSet;
import org.lsst.ccs.utilities.readout.GeometryFitsHeaderMetadataProvider;
import org.lsst.ccs.utilities.readout.PropertiesFitsHeaderMetadataProvider;
import org.lsst.ccs.utilities.readout.ReadOutImageSet;
import org.lsst.ccs.utilities.readout.ReadOutParameters;
import org.lsst.ccs.utilities.readout.ReadOutParametersBuilder;
import org.lsst.ccs.utilities.readout.ReadOutParametersNew;
import org.lsst.ccs.utilities.readout.ReadOutParametersOld;

/**
 *
 * @author tonyj
 */
public class FitsIntWriter implements WritableIntChannel {

    private static final FitsHeadersSpecificationsBuilder HEADER_SPEC_BUILDER = new FitsHeadersSpecificationsBuilder();

    static {
        FitsFactory.setUseHierarch(true);
        HEADER_SPEC_BUILDER.addSpecFile("primary.spec");
        HEADER_SPEC_BUILDER.addSpecFile("daqv4-primary.spec", "primary");
        HEADER_SPEC_BUILDER.addSpecFile("extended.spec");
    }

    private final Decompress18BitChannel decompress;

    public FitsIntWriter(Source source, Reb reb, FileNamer fileNamer) throws DAQException, IOException, FitsException {
        int ccdCount = source.getSourceType().getCCDCount();
        SourceMetaData smd = source.getMetaData();
        // Note, we are now using a single map for both the FileNameProperties and
        // for writing FITS file headers
        Map<String, Object> props = new HashMap<>();
        try {
            ImageName in = new ImageName(source.getImage().getMetaData().getName());
            props.put("ImageName", in.toString());
            props.put("ImageDate", in.getDateString());
            props.put("ImageNumber", in.getNumberString());
            props.put("ImageController", in.getController().getCode());
            props.put("ImageSource", in.getSource().getCode());
        } catch (IllegalArgumentException x) {
            props.put("ImageName", source.getImage().getMetaData().getName());
        }
        props.put("FileCreationTime", new Date());
        props.put("DAQTriggerTime", source.getImage().getMetaData().getTimestamp());
        props.put("Tag", String.format("%x", source.getImage().getMetaData().getId()));
        props.put("RaftBay", source.getLocation().getRaftName());
        props.put("RebSlot", source.getLocation().getBoardName());
        props.put("Firmware", String.format("%x", smd.getFirmware()));
        props.put("Platform", smd.getPlatform());
        props.put("CCDControllerSerial", String.format("%x", smd.getSerialNumber() & 0xFFFFFFFFL));
        props.put("DAQVersion", smd.getSoftware().toString());
        props.put("DAQPartition", source.getImage().getStore().getPartition());
        props.put("DAQFolder", source.getImage().getMetaData().getCreationFolderName());
        props.put("DAQAnnotation", source.getImage().getMetaData().getAnnotation());

        PropertiesFitsHeaderMetadataProvider propsFitsHeaderMetadataProvider = new PropertiesFitsHeaderMetadataProvider(props);
        // Open the FITS files (one per CCD) and write headers.
        File[] files = new File[source.getSourceType() == Source.SourceType.WAVEFRONT ? 2 : ccdCount];
        writers = new FitsFileWriter[files.length];
        ReadoutConfig readoutConfig = new ReadoutConfig(source.getSourceType());
        // TODO: 16 should not be hard-wired here
        WritableIntChannel[] fileChannels = new WritableIntChannel[ccdCount * 16];
        for (int i = 0; i < files.length; i++) {
            int sensorIndex = readoutConfig.getDataSensorMap()[i];
            props.put("CCDSlot", source.getLocation().getSensorName(sensorIndex));
            files[i] = fileNamer.computeFileName(props);
            CCD ccd = reb.getCCDs().get(sensorIndex);
            if (!ccd.getName().equals(props.get("CCDSlot"))) {
                throw new IOException(String.format("Geometry (%s) inconsistent with DAQ location (%s)",
                        ccd.getName(), props.get("CCDSlot")));
            }
            int[] registerValues = smd.getRegisterValues();
            ReadOutParametersBuilder builder = ReadOutParametersBuilder.create();
            builder.readoutParameterValues(registerValues);
            if (registerValues.length == 9) {
                // Assume old style meta-data
                builder.ccdType(ccd.getType()).readoutParameterNames(ReadOutParametersOld.DEFAULT_NAMES);
            } else {
                builder.readoutParameterNames(ReadOutParametersNew.DEFAULT_NAMES);
            }
            ReadOutParameters readoutParameters = builder.build();
            ImageSet imageSet = new ReadOutImageSet(ccd, readoutParameters);

            List<FitsHeaderMetadataProvider> providers = new ArrayList<>();
            //providers.add(rebNode.getFitsService().getFitsHeaderMetadataProvider(ccd.getUniqueId()));
            providers.add(new GeometryFitsHeaderMetadataProvider(ccd));
            providers.add(propsFitsHeaderMetadataProvider);
            writers[i] = new FitsFileWriter(files[i], imageSet, HEADER_SPEC_BUILDER.getHeaderSpecifications(), providers);

            for (int j = 0; j < imageSet.getNumberOfImages(); j++) {
                fileChannels[i * imageSet.getNumberOfImages() + j] = new FitsAsyncWriteChannel(writers[i], readoutConfig.getDataSegmentMap()[j]);
            }
        }
        DemultiplexingIntChannel demultiplex = new DemultiplexingIntChannel(fileChannels);
        XORWritableIntChannel xor = new XORWritableIntChannel(demultiplex, readoutConfig.getXor());
        decompress = new Decompress18BitChannel(xor);
    }
    private final FitsFileWriter[] writers;

    @Override
    public void write(int i) throws IOException {
        decompress.write(i);
    }

    @Override
    public void write(IntBuffer buffer) throws IOException {
        decompress.write(buffer);
    }

    @Override
    public boolean isOpen() {
        return decompress.isOpen();
    }

    @Override
    public void close() throws IOException {
        decompress.close();
        for (FitsFileWriter writer : writers) {
            if (writer != null) {
                writer.close();
            }
        }
    }

    public static interface FileNamer {

        File computeFileName(Map<String, Object> props);
    }

}
