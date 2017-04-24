package org.Orchestrator.app;

import org.onlab.packet.Ip4Address;
import org.onosproject.net.Host;

import java.lang.reflect.Array;
import java.security.InvalidParameterException;
import java.util.ArrayList;

public class FaultTolerantChain {
    public static final String DELIM = ",";
    private static final int MIN_SRC_CHAIN_DST_LEN = 3;
    private static int count = 0;

    private int chainId;
    private Ip4Address source;
    private Ip4Address destination;
    private byte f;
    private ArrayList<Byte> MBs;
//    ArrayList<Host> replicaMapping;
    ArrayList<Ip4Address> replicaMapping;
    private byte firstTag;

    public FaultTolerantChain(int chainId, Ip4Address source, Ip4Address destination, ArrayList<Byte> MBs, byte f)
            throws InvalidParameterException {
        this.source = source;
        this.destination = destination;
        if (!checkForFaultTolerance(f, (byte)MBs.size()))
            throw new InvalidParameterException(String.format("The chain with the length of %d is not long enough " +
                    "to tolerate %d failures!", f, MBs.size()));

        this.MBs = MBs;
        this.f = f;
        replicaMapping = new ArrayList<>();

        count = Math.max(count + 1, chainId);
    }

    public FaultTolerantChain(Ip4Address source, Ip4Address destination, ArrayList<Byte> MBs, byte f)
            throws InvalidParameterException {
        this(count, source, destination, MBs, f);
    }

    FaultTolerantChain() {
        ++count;
        replicaMapping = new ArrayList<>();
    }


    public int getChainId() {
        return chainId;
    }

    public void setChainId(int chainId) {
        this.chainId = chainId;
    }

//    public ArrayList<Host> getReplicaMapping() {
//        return replicaMapping;
//    }


    public ArrayList<Ip4Address> getReplicaMapping() {
        return replicaMapping;
    }

    public void appendToChain(byte MB) {
        MBs.add(MB);
    }

    public int length() {
        return MBs.size();
    }

    public byte getMB(int index) {
        return MBs.get(index);
    }

    void setF(byte f) {
        this.f = f;
    }

    public byte getF() {
        return f;
    }

    public Ip4Address getSource() {
        return source;
    }

    public Ip4Address getDestination() {
        return destination;
    }

    public static FaultTolerantChain parse(String srcChainDst, byte f) throws InvalidParameterException {
        String[] elems = srcChainDst.split(DELIM);
        if (elems.length < MIN_SRC_CHAIN_DST_LEN)
            throw new InvalidParameterException(String.format("The length of (source + chain + destination) " +
                    "must be higher than %d", MIN_SRC_CHAIN_DST_LEN));

        ArrayList<Byte> MBs = new ArrayList<>(elems.length - 2);
        for (int i = 0; i < elems.length - 2; ++i)
            MBs.add(Byte.parseByte(elems[i + 1]));

        return new FaultTolerantChain(
                    Ip4Address.valueOf(elems[0]),
                    Ip4Address.valueOf(elems[elems.length - 1]),
                    MBs,
                    f);
    }

    public static boolean checkForFaultTolerance(byte f, byte n) {
        return n >= f + 1;
    }

    public ArrayList<Ip4Address> getChainIpAddresses() {
        ArrayList<Ip4Address> chainIpAddresses = new ArrayList<Ip4Address>();
        chainIpAddresses.add(source);
        for (byte i=0; i < replicaMapping.size(); ++i){
            chainIpAddresses.add(replicaMapping.get(i));
        }
        chainIpAddresses.add(destination);
        return chainIpAddresses;
    }

    public byte getFirstTag() {
        return firstTag;
    }

    public void setFirstTag(byte firstTag) {
        this.firstTag = firstTag;
    }
}