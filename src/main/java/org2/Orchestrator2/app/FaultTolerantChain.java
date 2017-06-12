package org2.Orchestrator2.app;

import org.onosproject.net.Host;

import java.security.InvalidParameterException;
import java.util.ArrayList;

public class FaultTolerantChain {

    private static int count = 0;

    private int chainId;
    private Host source;
    private Host destination;
    private byte f;
    private ArrayList<Byte> MBs;
    ArrayList<Host> replicaMapping;
    private byte firstTag;

    public FaultTolerantChain(int chainId, Host source, Host destination, ArrayList<Byte> MBs, byte f)
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

    public FaultTolerantChain(Host source, Host destination, ArrayList<Byte> MBs, byte f)
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


    public ArrayList<Host> getReplicaMapping() {
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

    public Host getSource() {
        return source;
    }

    public Host getDestination() {
        return destination;
    }

    public static boolean checkForFaultTolerance(byte f, byte n) {
        return n >= f + 1;
    }

    public ArrayList<Host> getChainHosts() {
        ArrayList<Host> chainHosts = new ArrayList<Host>();
        chainHosts.add(source);
        for (byte i=0; i < replicaMapping.size(); ++i){
            chainHosts.add(replicaMapping.get(i));
        }
        chainHosts.add(destination);
        return chainHosts;
    }

    public void setFirstTag(byte firstTag) {
        this.firstTag = firstTag;
    }

    public byte getFirstTag() {
        return firstTag;
    }
}
