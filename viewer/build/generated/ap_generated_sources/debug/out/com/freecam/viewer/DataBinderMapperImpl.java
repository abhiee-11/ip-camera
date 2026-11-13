package com.freecam.viewer;

import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.View;
import androidx.databinding.DataBinderMapper;
import androidx.databinding.DataBindingComponent;
import androidx.databinding.ViewDataBinding;
import com.freecam.viewer.databinding.ActivityAddCameraBindingImpl;
import com.freecam.viewer.databinding.ActivityEventsBindingImpl;
import com.freecam.viewer.databinding.ActivityHistoryBindingImpl;
import com.freecam.viewer.databinding.ActivityLiveBindingImpl;
import com.freecam.viewer.databinding.ActivityMainBindingImpl;
import com.freecam.viewer.databinding.ActivityPlayerBindingImpl;
import com.freecam.viewer.databinding.ActivitySettingsBindingImpl;
import com.freecam.viewer.databinding.ItemCameraBindingImpl;
import com.freecam.viewer.databinding.ItemDayChipBindingImpl;
import com.freecam.viewer.databinding.ItemEventBindingImpl;
import com.freecam.viewer.databinding.ItemRecordingBindingImpl;
import java.lang.IllegalArgumentException;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.RuntimeException;
import java.lang.String;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DataBinderMapperImpl extends DataBinderMapper {
  private static final int LAYOUT_ACTIVITYADDCAMERA = 1;

  private static final int LAYOUT_ACTIVITYEVENTS = 2;

  private static final int LAYOUT_ACTIVITYHISTORY = 3;

  private static final int LAYOUT_ACTIVITYLIVE = 4;

  private static final int LAYOUT_ACTIVITYMAIN = 5;

  private static final int LAYOUT_ACTIVITYPLAYER = 6;

  private static final int LAYOUT_ACTIVITYSETTINGS = 7;

  private static final int LAYOUT_ITEMCAMERA = 8;

  private static final int LAYOUT_ITEMDAYCHIP = 9;

  private static final int LAYOUT_ITEMEVENT = 10;

  private static final int LAYOUT_ITEMRECORDING = 11;

  private static final SparseIntArray INTERNAL_LAYOUT_ID_LOOKUP = new SparseIntArray(11);

  static {
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.freecam.viewer.R.layout.activity_add_camera, LAYOUT_ACTIVITYADDCAMERA);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.freecam.viewer.R.layout.activity_events, LAYOUT_ACTIVITYEVENTS);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.freecam.viewer.R.layout.activity_history, LAYOUT_ACTIVITYHISTORY);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.freecam.viewer.R.layout.activity_live, LAYOUT_ACTIVITYLIVE);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.freecam.viewer.R.layout.activity_main, LAYOUT_ACTIVITYMAIN);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.freecam.viewer.R.layout.activity_player, LAYOUT_ACTIVITYPLAYER);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.freecam.viewer.R.layout.activity_settings, LAYOUT_ACTIVITYSETTINGS);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.freecam.viewer.R.layout.item_camera, LAYOUT_ITEMCAMERA);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.freecam.viewer.R.layout.item_day_chip, LAYOUT_ITEMDAYCHIP);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.freecam.viewer.R.layout.item_event, LAYOUT_ITEMEVENT);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.freecam.viewer.R.layout.item_recording, LAYOUT_ITEMRECORDING);
  }

  @Override
  public ViewDataBinding getDataBinder(DataBindingComponent component, View view, int layoutId) {
    int localizedLayoutId = INTERNAL_LAYOUT_ID_LOOKUP.get(layoutId);
    if(localizedLayoutId > 0) {
      final Object tag = view.getTag();
      if(tag == null) {
        throw new RuntimeException("view must have a tag");
      }
      switch(localizedLayoutId) {
        case  LAYOUT_ACTIVITYADDCAMERA: {
          if ("layout/activity_add_camera_0".equals(tag)) {
            return new ActivityAddCameraBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_add_camera is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYEVENTS: {
          if ("layout/activity_events_0".equals(tag)) {
            return new ActivityEventsBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_events is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYHISTORY: {
          if ("layout/activity_history_0".equals(tag)) {
            return new ActivityHistoryBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_history is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYLIVE: {
          if ("layout/activity_live_0".equals(tag)) {
            return new ActivityLiveBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_live is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYMAIN: {
          if ("layout/activity_main_0".equals(tag)) {
            return new ActivityMainBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_main is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYPLAYER: {
          if ("layout/activity_player_0".equals(tag)) {
            return new ActivityPlayerBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_player is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYSETTINGS: {
          if ("layout/activity_settings_0".equals(tag)) {
            return new ActivitySettingsBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_settings is invalid. Received: " + tag);
        }
        case  LAYOUT_ITEMCAMERA: {
          if ("layout/item_camera_0".equals(tag)) {
            return new ItemCameraBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for item_camera is invalid. Received: " + tag);
        }
        case  LAYOUT_ITEMDAYCHIP: {
          if ("layout/item_day_chip_0".equals(tag)) {
            return new ItemDayChipBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for item_day_chip is invalid. Received: " + tag);
        }
        case  LAYOUT_ITEMEVENT: {
          if ("layout/item_event_0".equals(tag)) {
            return new ItemEventBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for item_event is invalid. Received: " + tag);
        }
        case  LAYOUT_ITEMRECORDING: {
          if ("layout/item_recording_0".equals(tag)) {
            return new ItemRecordingBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for item_recording is invalid. Received: " + tag);
        }
      }
    }
    return null;
  }

  @Override
  public ViewDataBinding getDataBinder(DataBindingComponent component, View[] views, int layoutId) {
    if(views == null || views.length == 0) {
      return null;
    }
    int localizedLayoutId = INTERNAL_LAYOUT_ID_LOOKUP.get(layoutId);
    if(localizedLayoutId > 0) {
      final Object tag = views[0].getTag();
      if(tag == null) {
        throw new RuntimeException("view must have a tag");
      }
      switch(localizedLayoutId) {
      }
    }
    return null;
  }

  @Override
  public int getLayoutId(String tag) {
    if (tag == null) {
      return 0;
    }
    Integer tmpVal = InnerLayoutIdLookup.sKeys.get(tag);
    return tmpVal == null ? 0 : tmpVal;
  }

  @Override
  public String convertBrIdToString(int localId) {
    String tmpVal = InnerBrLookup.sKeys.get(localId);
    return tmpVal;
  }

  @Override
  public List<DataBinderMapper> collectDependencies() {
    ArrayList<DataBinderMapper> result = new ArrayList<DataBinderMapper>(1);
    result.add(new androidx.databinding.library.baseAdapters.DataBinderMapperImpl());
    return result;
  }

  private static class InnerBrLookup {
    static final SparseArray<String> sKeys = new SparseArray<String>(1);

    static {
      sKeys.put(0, "_all");
    }
  }

  private static class InnerLayoutIdLookup {
    static final HashMap<String, Integer> sKeys = new HashMap<String, Integer>(11);

    static {
      sKeys.put("layout/activity_add_camera_0", com.freecam.viewer.R.layout.activity_add_camera);
      sKeys.put("layout/activity_events_0", com.freecam.viewer.R.layout.activity_events);
      sKeys.put("layout/activity_history_0", com.freecam.viewer.R.layout.activity_history);
      sKeys.put("layout/activity_live_0", com.freecam.viewer.R.layout.activity_live);
      sKeys.put("layout/activity_main_0", com.freecam.viewer.R.layout.activity_main);
      sKeys.put("layout/activity_player_0", com.freecam.viewer.R.layout.activity_player);
      sKeys.put("layout/activity_settings_0", com.freecam.viewer.R.layout.activity_settings);
      sKeys.put("layout/item_camera_0", com.freecam.viewer.R.layout.item_camera);
      sKeys.put("layout/item_day_chip_0", com.freecam.viewer.R.layout.item_day_chip);
      sKeys.put("layout/item_event_0", com.freecam.viewer.R.layout.item_event);
      sKeys.put("layout/item_recording_0", com.freecam.viewer.R.layout.item_recording);
    }
  }
}
