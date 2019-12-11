package github.tornaco.thanos.android.module.profile;

import android.app.Application;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.AndroidViewModel;

import com.google.common.io.CharStreams;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import github.tornaco.android.thanos.core.app.ThanosManager;
import github.tornaco.android.thanos.core.profile.ProfileManager;
import github.tornaco.android.thanos.core.profile.RuleAddCallback;
import github.tornaco.android.thanos.core.profile.RuleInfo;
import github.tornaco.android.thanos.core.util.Rxs;
import github.tornaco.android.thanos.core.util.Timber;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import lombok.Getter;
import rx2.android.schedulers.AndroidSchedulers;

public class RuleListViewModel extends AndroidViewModel {

    @Getter
    private final ObservableBoolean isDataLoading = new ObservableBoolean(false);
    private final CompositeDisposable disposables = new CompositeDisposable();
    @Getter
    private final ObservableArrayList<RuleInfo> ruleInfoList = new ObservableArrayList<>();

    private RuleLoader loader = () -> new ArrayList<>(Arrays.asList(
            ThanosManager.from(getApplication())
                    .getProfileManager()
                    .getAllRules()));

    public RuleListViewModel(@NonNull Application application) {
        super(application);
        registerEventReceivers();
    }

    public void start() {
        loadModels();
    }

    public void resume() {
        loadModels();
    }

    private void loadModels() {
        if (!ThanosManager.from(getApplication()).isServiceInstalled()) return;
        if (isDataLoading.get()) return;
        isDataLoading.set(true);
        disposables.add(Single
                .create((SingleOnSubscribe<List<RuleInfo>>) emitter ->
                        emitter.onSuccess(Objects.requireNonNull(loader.load())))
                .flatMapObservable((Function<List<RuleInfo>,
                        ObservableSource<RuleInfo>>) Observable::fromIterable)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(disposable -> ruleInfoList.clear())
                .subscribe(ruleInfoList::add, Rxs.ON_ERROR_LOGGING, () -> isDataLoading.set(false)));
    }

    private void registerEventReceivers() {
    }

    private void unRegisterEventReceivers() {
    }

    @SuppressWarnings("UnstableApiUsage")
    private String readTextFromUri(Uri uri) {
        try {
            return CharStreams.toString(new InputStreamReader(
                    Objects.requireNonNull(getApplication().getContentResolver().openInputStream(uri))
                    , StandardCharsets.UTF_8));
        } catch (Exception e) {
            Timber.e(e);
            return null;
        }
    }

    void importRuleFromUri(Uri uri) {
        try {
            String ruleString = readTextFromUri(uri);
            Timber.d(ruleString);
            RuleAddCallback callback = new RuleAddCallback() {
                @Override
                protected void onRuleAddSuccess() {
                    super.onRuleAddSuccess();
                    Toast.makeText(getApplication(),
                            R.string.module_profile_editor_save_success,
                            Toast.LENGTH_LONG)
                            .show();
                }
            };
            ThanosManager.from(getApplication())
                    .getProfileManager()
                    .addRule(ruleString, callback, ProfileManager.RULE_FORMAT_JSON);
            ThanosManager.from(getApplication())
                    .getProfileManager()
                    .addRule(ruleString, callback, ProfileManager.RULE_FORMAT_YAML);
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    void importRuleExamples() {
        try {
            String[] ruleFiles = getApplication().getAssets().list("prebuilt_profile");
            if (ruleFiles == null) {
                return;
            }
            for (String file : ruleFiles) {
                int type = ProfileManager.RULE_FORMAT_JSON;
                if (file.endsWith("yml")) {
                    type = ProfileManager.RULE_FORMAT_YAML;
                }
                InputStream inputStream = getApplication().getAssets().open("prebuilt_profile/" + file);
                String ruleString = CharStreams.toString(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                ThanosManager.from(getApplication())
                        .getProfileManager()
                        .addRule(ruleString, new RuleAddCallback() {
                            @Override
                            protected void onRuleAddFail(int errorCode, String errorMessage) {
                                super.onRuleAddFail(errorCode, errorMessage);
                                Toast.makeText(getApplication(),
                                        R.string.module_profile_editor_save_check_error,
                                        Toast.LENGTH_LONG)
                                        .show();
                            }
                        }, type);
            }
            Toast.makeText(getApplication(),
                    R.string.module_profile_editor_save_success,
                    Toast.LENGTH_LONG)
                    .show();
            // Reload.
            resume();
        } catch (IOException e) {
            Timber.e(e);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
        unRegisterEventReceivers();
    }

    public interface RuleLoader {
        @WorkerThread
        List<RuleInfo> load();
    }
}
