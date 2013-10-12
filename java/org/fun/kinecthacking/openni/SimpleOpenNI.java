package org.fun.kinecthacking.openni;

import org.OpenNI.Context;
import org.OpenNI.GeneralException;
import org.OpenNI.Version;

public class SimpleOpenNI {

    public static void main(String [] argv) {
        try {
            Version version = Context.getVersion();
            System.out.println(String.format("Major : %d, minor : %d, maintenance : %d, build : %d",
                                             version.getMajor(), version.getMinor(),
                                             version.getMaintenance(), version.getBuild()));
        } catch (GeneralException e) {
            throw new RuntimeException(e);
        }
    }
}
