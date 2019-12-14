package github.tornaco.android.thanos.apps;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

import github.tornaco.android.thanos.R;
import github.tornaco.android.thanos.common.AppItemViewClickListener;
import github.tornaco.android.thanos.common.AppListModel;
import github.tornaco.android.thanos.common.CommonAppListFilterActivity;
import github.tornaco.android.thanos.common.CommonAppListFilterViewModel;
import github.tornaco.android.thanos.core.app.ThanosManager;
import github.tornaco.android.thanos.core.pm.AppInfo;
import github.tornaco.android.thanos.util.ActivityUtils;
import github.tornaco.android.thanos.widget.SwitchBar;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;

public class AppsManageActivity extends CommonAppListFilterActivity {

    public static void start(Context context) {
        ActivityUtils.startActivity(context, AppsManageActivity.class);
    }

    @Override
    protected int getTitleRes() {
        return R.string.activity_title_apps_manager;
    }

    @NonNull
    @Override
    protected AppItemViewClickListener onCreateAppItemViewClickListener() {
        return appInfo -> AppDetailsActivity.start(AppsManageActivity.this, appInfo);
    }

    @NonNull
    @Override
    protected CommonAppListFilterViewModel.ListModelLoader onCreateListModelLoader() {
        String runningBadge = getString(R.string.badge_app_running);
        String idleBadge = getApplicationContext().getString(R.string.badge_app_idle);
        return index -> {
            ThanosManager thanos = ThanosManager.from(getApplicationContext());
            if (!thanos.isServiceInstalled()) {
                return Lists.newArrayList(new AppListModel(AppInfo.dummy()));
            }
            List<AppListModel> res = new ArrayList<>();
            CompositeDisposable disposable = new CompositeDisposable();
            disposable.add(Observable.fromArray(thanos.getPkgManager().getInstalledPkgs(index.flag))
                    .distinct()
                    .doOnComplete(disposable::dispose)
                    .subscribe(appInfo -> res.add(new AppListModel(
                            appInfo,
                            thanos.getActivityManager().isPackageRunning(appInfo.getPkgName()) ? runningBadge : null,
                            thanos.getActivityManager().isPackageIdle(appInfo.getPkgName()) ? idleBadge : null))));
            return res;
        };
    }

    @Override
    protected void onSetupSwitchBar(SwitchBar switchBar) {
        super.onSetupSwitchBar(switchBar);
        switchBar.hide();
    }
}
