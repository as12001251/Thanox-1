package github.tornaco.thanos.android.ops.model;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import github.tornaco.thanos.android.ops.R;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class OpsTemplate {

    public github.tornaco.android.thanos.core.secure.ops.OpsTemplate legacy;

    @StringRes
    public final int titleRes;
    @StringRes
    public final int summaryRes;
    @DrawableRes
    public final int iconRes;

    public final int sort;

    public static final OpsTemplate LOCATION_TEMPLATE = new OpsTemplate(
            github.tornaco.android.thanos.core.secure.ops.OpsTemplate.LOCATION_TEMPLATE,
            R.string.module_ops_category_location,
            R.string.module_ops_category_location,
            R.drawable.ic_settings_fill,
            0
    );

    public static final OpsTemplate PERSONAL_TEMPLATE = new OpsTemplate(
            github.tornaco.android.thanos.core.secure.ops.OpsTemplate.PERSONAL_TEMPLATE,
            R.string.module_ops_category_personal,
            R.string.module_ops_category_personal,
            R.drawable.ic_settings_fill,
            1
    );

    public static final OpsTemplate MESSAGING_TEMPLATE = new OpsTemplate(
            github.tornaco.android.thanos.core.secure.ops.OpsTemplate.MESSAGING_TEMPLATE,
            R.string.module_ops_category_message,
            R.string.module_ops_category_message,
            R.drawable.ic_settings_fill,
            2
    );

    public static final OpsTemplate MEDIA_TEMPLATE = new OpsTemplate(
            github.tornaco.android.thanos.core.secure.ops.OpsTemplate.MEDIA_TEMPLATE,
            R.string.module_ops_category_media,
            R.string.module_ops_category_media,
            R.drawable.ic_settings_fill,
            3
    );

    public static final OpsTemplate DEVICE_TEMPLATE = new OpsTemplate(
            github.tornaco.android.thanos.core.secure.ops.OpsTemplate.DEVICE_TEMPLATE,
            R.string.module_ops_category_device,
            R.string.module_ops_category_device,
            R.drawable.ic_settings_fill,
            4
    );

    public static final OpsTemplate RUN_IN_BACKGROUND_TEMPLATE = new OpsTemplate(
            github.tornaco.android.thanos.core.secure.ops.OpsTemplate.RUN_IN_BACKGROUND_TEMPLATE,
            R.string.module_ops_category_bg,
            R.string.module_ops_category_bg,
            R.drawable.ic_settings_fill,
            5
    );

    public static final OpsTemplate THANOX_TEMPLATE = new OpsTemplate(
            github.tornaco.android.thanos.core.secure.ops.OpsTemplate.THANOX_TEMPLATE,
            R.string.module_ops_category_thanox,
            R.string.module_ops_category_thanox,
            R.drawable.ic_shield_cross_line,
            6
    );

    // this template should contain all ops which are not part of any other template in
    // ALL_TEMPLATES
    public static final OpsTemplate REMAINING_TEMPLATE = new OpsTemplate(
            github.tornaco.android.thanos.core.secure.ops.OpsTemplate.REMAINING_TEMPLATE,
            R.string.module_ops_category_remaining,
            R.string.module_ops_category_remaining,
            R.drawable.ic_settings_fill,
            7
    );


    // this template contains all permissions grouped by templates
    public static final OpsTemplate[] ALL_PERMS_TEMPLATES = new OpsTemplate[]{
            LOCATION_TEMPLATE, PERSONAL_TEMPLATE, MESSAGING_TEMPLATE,
            MEDIA_TEMPLATE, DEVICE_TEMPLATE, RUN_IN_BACKGROUND_TEMPLATE,
            THANOX_TEMPLATE,
            REMAINING_TEMPLATE
    };
}
