package github.tornaco.android.thanos.core.pm;

import android.content.pm.PackageManager;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

import github.tornaco.android.thanos.BuildProp;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import util.PinyinComparatorUtils;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class AppInfo implements Parcelable, Comparable<AppInfo> {

    public static final int FLAGS_NONE = 0;
    public static final int FLAGS_USER = 0x00000001;
    public static final int FLAGS_SYSTEM = 0x00000002;
    public static final int FLAGS_SYSTEM_UID = 0x00000004;
    public static final int FLAGS_SYSTEM_MEDIA = 0x00000008;
    public static final int FLAGS_SYSTEM_PHONE = 0x000000010;
    public static final int FLAGS_WHITE_LISTED = 0x000000020;
    public static final int FLAGS_WEB_VIEW_PROVIDER = 0x000000040;

    public static final int FLAGS_ALL = FLAGS_USER
            | FLAGS_SYSTEM
            | FLAGS_SYSTEM_UID
            | FLAGS_SYSTEM_MEDIA
            | FLAGS_SYSTEM_PHONE
            | FLAGS_WEB_VIEW_PROVIDER
            | FLAGS_WHITE_LISTED;

    private String pkgName;
    private String appLabel;
    private int versionCode;
    private String versionName;
    private int flags;
    private int uid;
    // Enabled or disabled?
    private int state;
    // Idle or not?
    private boolean idle;
    private boolean isDummy;
    // Ignore Parcelable
    private boolean isSelected;

    private AppInfo(Parcel in) {
        pkgName = in.readString();
        appLabel = in.readString();
        versionCode = in.readInt();
        versionName = in.readString();
        flags = in.readInt();
        uid = in.readInt();
        state = in.readInt();
        idle = in.readInt() == 1;
        isDummy = in.readInt() == 1;
    }

    public static final Creator<AppInfo> CREATOR = new Creator<AppInfo>() {
        @Override
        public AppInfo createFromParcel(Parcel in) {
            return new AppInfo(in);
        }

        @Override
        public AppInfo[] newArray(int size) {
            return new AppInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.pkgName);
        dest.writeString(this.appLabel);
        dest.writeInt(this.versionCode);
        dest.writeString(this.versionName);
        dest.writeInt(this.flags);
        dest.writeInt(this.uid);
        dest.writeInt(this.state);
        dest.writeInt(this.idle ? 1 : 0);
        dest.writeInt(this.isDummy ? 1 : 0);
    }


    @SuppressWarnings("NullableProblems")
    @Override
    public int compareTo(AppInfo appInfo) {
        if (appInfo == null) return -1;
        if (this.isSelected && !appInfo.isSelected) {
            return -1;
        }
        if (!this.disabled() && appInfo.disabled()) {
            return -1;
        }
        return PinyinComparatorUtils.compare(this.appLabel, appInfo.appLabel);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppInfo appInfo = (AppInfo) o;
        return pkgName.equals(appInfo.pkgName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pkgName);
    }

    public boolean disabled() {
        return state != PackageManager.COMPONENT_ENABLED_STATE_ENABLED && state != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
    }

    public static AppInfo dummy() {
        return new AppInfo(
                BuildProp.THANOS_APP_PKG_NAME,
                "Dummy",
                0,
                "0",
                FLAGS_USER,
                Integer.MAX_VALUE,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                false,
                false,
                true);
    }
}
