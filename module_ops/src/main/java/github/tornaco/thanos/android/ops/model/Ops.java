package github.tornaco.thanos.android.ops.model;

import android.content.Context;
import android.content.res.TypedArray;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import java.util.Arrays;

import github.tornaco.android.thanos.core.annotation.Nullable;
import github.tornaco.android.thanos.core.util.Preconditions;
import github.tornaco.thanos.android.ops.R;

import static github.tornaco.android.thanos.core.secure.ops.AppOpsManager._NUM_OP;

@SuppressWarnings("WeakerAccess")
public class Ops {

    @Nullable
    public static OpsTemplate templateOfOp(int op) {
        for (OpsTemplate template : OpsTemplate.ALL_PERMS_TEMPLATES) {
            if (Arrays.binarySearch(template.legacy.ops, op) >= 0) {
                return template;
            }
        }
        return null;
    }

    @Nullable
    public static Op toOp(@NonNull Context context, int op, int mode) {
        return Op.builder()
                .title(opLabel(context, op))
                .summary(opSummary(context, op))
                .code(op)
                .iconRes(opIcon(context, op))
                .mode(mode)
                .build();
    }

    @Nullable
    public static Op toOp(@NonNull Context context, int op, boolean remind) {
        return Op.builder()
                .title(opLabel(context, op))
                .summary(opSummary(context, op))
                .code(op)
                .iconRes(opIcon(context, op))
                .remind(remind)
                .build();
    }

    @Nullable
    public static Op toOp(@NonNull Context context, int op) {
        return Op.builder()
                .title(opLabel(context, op))
                .summary(opSummary(context, op))
                .code(op)
                .iconRes(opIcon(context, op))
                .build();
    }

    @DrawableRes
    public static int opIcon(@NonNull Context context, int op) {
        TypedArray array = context.getResources().obtainTypedArray(R.array.module_ops_op_icon);
        try {
            return array.getResourceId(op, R.drawable.module_ops_ic_contacts_fill /* fallback */);
        } finally {
            array.recycle();
        }
    }

    @NonNull
    public static String opLabel(@NonNull Context context, int op) {
        String[] array = context.getResources().getStringArray(R.array.module_ops_app_ops_labels);
        Preconditions.checkArgument(array.length == _NUM_OP, "Bad label array, expected: " + _NUM_OP + ", got: " + array.length);
        return array[op];
    }

    @NonNull
    public static String opSummary(@NonNull Context context, int op) {
        String[] array = context.getResources().getStringArray(R.array.module_ops_app_ops_summaries);
        Preconditions.checkArgument(array.length == _NUM_OP, "Bad summary array, expected: " + _NUM_OP + ", got: " + array.length);
        return array[op];
    }
}
