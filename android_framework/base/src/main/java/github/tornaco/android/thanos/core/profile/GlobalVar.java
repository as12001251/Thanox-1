package github.tornaco.android.thanos.core.profile;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

import github.tornaco.android.thanos.core.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public final class GlobalVar implements Parcelable {
    private final static Gson GSON = new Gson();

    private String name;
    private List<String> stringList;

    private GlobalVar(Parcel in) {
        name = in.readString();
        stringList = in.createStringArrayList();
    }

    public static final Creator<GlobalVar> CREATOR = new Creator<GlobalVar>() {
        @Override
        public GlobalVar createFromParcel(Parcel in) {
            return new GlobalVar(in);
        }

        @Override
        public GlobalVar[] newArray(int size) {
            return new GlobalVar[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(name);
        parcel.writeStringList(stringList);
    }

    @Nullable
    public String listToJson() {
        if (this.stringList == null) return null;
        return GSON.toJson(this.stringList);
    }

    @Nullable
    public static GlobalVar fromJson(String json) {
        if (json == null) return null;
        try {
            return GSON.fromJson(json, GlobalVar.class);
        } catch (Throwable e) {
            return null;
        }
    }

    @Nullable
    public static ArrayList<String> listFromJson(String json) {
        if (json == null) return null;
        try {
            return GSON.fromJson(json, new TypeToken<ArrayList<String>>() {
            }.getType());
        } catch (Throwable e) {
            return null;
        }
    }
}
