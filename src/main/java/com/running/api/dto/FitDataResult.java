import com.garmin.fit.*;
import java.util.ArrayList;
import java.util.List;

public class FitDataResult {
    private FileIdMesg fileId;
    private SessionMesg session;
    private ActivityMesg activity;
    private FileCreatorMesg fileCreator;
    private final List<DeviceInfoMesg> devices = new ArrayList<>();
    private final List<UserProfileMesg> userProfiles = new ArrayList<>();
    private final List<ZonesTargetMesg> zonesTargets = new ArrayList<>();
    private final List<LapMesg> laps = new ArrayList<>();
    private final List<RecordMesg> records = new ArrayList<>();
    private final List<EventMesg> events = new ArrayList<>();
    private final List<HrMesg> hrData = new ArrayList<>();
    private final List<Mesg> unhandledMessages = new ArrayList<>();

    // Getters y Setters
    public FileIdMesg getFileId() { return fileId; }
    public void setFileId(FileIdMesg fileId) { this.fileId = fileId; }
    public SessionMesg getSession() { return session; }
    public void setSession(SessionMesg session) { this.session = session; }
    public ActivityMesg getActivity() { return activity; }
    public void setActivity(ActivityMesg activity) { this.activity = activity; }
    public FileCreatorMesg getFileCreator() { return fileCreator; }
    public void setFileCreator(FileCreatorMesg fileCreator) { this.fileCreator = fileCreator; }
    public List<DeviceInfoMesg> getDevices() { return devices; }
    public List<UserProfileMesg> getUserProfiles() { return userProfiles; }
    public List<ZonesTargetMesg> getZonesTargets() { return zonesTargets; }
    public List<LapMesg> getLaps() { return laps; }
    public List<RecordMesg> getRecords() { return records; }
    public List<EventMesg> getEvents() { return events; }
    public List<HrMesg> getHrData() { return hrData; }
    public List<Mesg> getUnhandledMessages() { return unhandledMessages; }
}