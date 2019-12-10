package github.tornaco.android.thanos.core

abstract class Res {

    object Strings {

        const val STRING_WHILE_LIST_PACKAGES = "white_list_packages"
        const val STRING_WHILE_LIST_PACKAGES_HOOKS = "white_list_packages_hooks"
        const val STRING_APPLOCK_WHITE_LIST_ACTIVITY = "app_lock_white_list_activity"
        const val STRING_START_BLOCKER_CALLER_WHITELIST = "start_blocker_caller_whitelist"
        const val STRING_OP_REMIND_WHITELIST = "op_remind_whitelist"
        const val STRING_TASK_REMOVAL_MULTIPLE_TASK_CHECK_LIST = "task_removal_multiple_task_check_list"

        const val STRING_SERVICE_SILENCE_NOTIFICATION_CHANNEL = "service_silence_notification_channel"
        const val STRING_SERVICE_NOTIFICATION_OVERRIDE_THANOS = "service_notification_override_thanos"

        const val STRING_SERVICE_NOTIFICATION_TITLE_BG_RESTRICT_PROCESS_CHANGED =
            "service_notification_title_bg_restrict_process_changed"

        const val STRING_SERVICE_NOTIFICATION_CONTENT_BG_RESTRICT_PROCESS_CHANGED =
            "service_notification_content_bg_restrict_process_changed"

        const val STRING_SERVICE_NOTIFICATION_ACTION_BG_RESTRICT_PROCESS_CHANGED_CLEAR =
            "service_notification_action_bg_restrict_process_changed_clear"

        const val STRING_SERVICE_NOTIFICATION_ACTION_BG_RESTRICT_PROCESS_CHANGED_VIEW =
            "service_notification_action_bg_restrict_process_changed_view"

        const val STRING_SERVICE_NOTIFICATION_TITLE_OP_START_REMIND =
            "service_notification_title_op_start_remind"

        const val STRING_SERVICE_OP_LABEL_CAMERA = "service_op_label_camera"
        const val STRING_SERVICE_OP_LABEL_RECORD_AUDIO = "service_op_label_record_audio"
        const val STRING_SERVICE_OP_LABEL_PLAY_AUDIO = "service_op_label_play_audio"
        const val STRING_SERVICE_OP_LABEL_LOCATION = "service_op_label_location"

        const val STRING_BG_TASKS_CLEAN_COMPLETE = "service_notification_bg_tasks_clean_complete"

        const val STRING_SERVICE_TOAST_CURRENT_COMPONENT_COPIED_TO_CLIPBOARD = "service_toast_current_component_copied_to_clipboard"
    }

    object Drawables {
        const val DRAWABLE_ROCKET_2_FILL = "ic_rocket_fill_system"
        const val DRAWABLE_EYE_CLOSE_FILL = "ic_eye_close_fill_system"
        const val DRAWABLE_CAMERA_FILL = "ic_camera_fill"
        const val DRAWABLE_MIC_FILL = "ic_mic_2_fill"
        const val DRAWABLE_MUSIC_FILL = "module_ops_ic_music_fill"
        const val DRAWABLE_MAP_PIN_FILL = "ic_map_pin_2_fill"
        const val DRAWABLE_CHECK_DOUBLE_FILL = "ic_check_double_fill"
        const val DRAWABLE_HEART_FILL = "ic_heart_2_fill"
    }

}

