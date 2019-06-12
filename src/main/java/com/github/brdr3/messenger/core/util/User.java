package com.github.brdr3.messenger.core.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class User {
    private String username;
    private InetAddress address;
    private Integer port;
    
    public User(String username, InetAddress address, Integer sendPort) {
        this.username = username;
        this.address = address;
        this.port = sendPort;
    }
    
    public User(String username) throws UnknownHostException {
        this(username, InetAddress.getByName("localhost"), 10);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public InetAddress getAddress() {
        return address;
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return username + "@" + address.getHostName() + ":" + port;
    }
    
    @Override
    public User clone() throws CloneNotSupportedException {
        return this;
    }
    
    @Override
    public boolean equals(Object o) {
        if(o instanceof User) {
            User oo = (User) o;
            
            return oo.getUsername().equals(this.getUsername());
        }
        
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 71 * hash + Objects.hashCode(this.username);
        hash = 71 * hash + Objects.hashCode(this.address);
        hash = 71 * hash + Objects.hashCode(this.port);
        return hash;
    }
}
