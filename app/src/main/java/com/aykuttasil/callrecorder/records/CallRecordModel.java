package com.aykuttasil.callrecorder.records;

import com.github.tamir7.contacts.Contact;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Transient;
import java.util.ArrayList;
import java.util.Date;
import io.objectbox.annotation.Generated;
import java.util.List;

/**
 * Created by tarun on 08/04/17.
 */

@Entity
public class CallRecordModel {

    @Id
    private long id;

    private int callType; // 0 incoming 1 - outgoing 2 - missed
    private String callStatus; // Recording // Recorded

    private String callNote;
    private String callerNumber;
    private String recordLocation;
    private long callDuration;
    private Date recordedDate;

    @Transient List<Contact> contacts = new ArrayList<>();
    @Transient boolean isSearched;

    @Generated(hash = 1713196187)
    public CallRecordModel(long id, int callType, String callStatus,
            String callNote, String callerNumber, String recordLocation,
            long callDuration, Date recordedDate) {
        this.id = id;
        this.callType = callType;
        this.callStatus = callStatus;
        this.callNote = callNote;
        this.callerNumber = callerNumber;
        this.recordLocation = recordLocation;
        this.callDuration = callDuration;
        this.recordedDate = recordedDate;
    }
    @Generated(hash = 1148161107)
    public CallRecordModel() {
    }
    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }
    public String getCallNote() {
        return callNote;
    }
    public void setCallNote(String callNote) {
        this.callNote = callNote;
    }
    public String getCallerNumber() {
        return callerNumber;
    }
    public void setCallerNumber(String callerNumber) {
        this.callerNumber = callerNumber;
    }
    public String getRecordLocation() {
        return recordLocation;
    }
    public void setRecordLocation(String recordLocation) {
        this.recordLocation = recordLocation;
    }
    public long getCallDuration() {
        return callDuration;
    }
    public void setCallDuration(long callDuration) {
        this.callDuration = callDuration;
    }
    public Date getRecordedDate() {
        return recordedDate;
    }
    public void setRecordedDate(Date recordedDate) {
        this.recordedDate = recordedDate;
    }
    public int getCallType() {
        return callType;
    }
    public void setCallType(int callType) {
        this.callType = callType;
    }
    public String getCallStatus() {
        return callStatus;
    }
    public void setCallStatus(String callStatus) {
        this.callStatus = callStatus;
    }
    
   
}
