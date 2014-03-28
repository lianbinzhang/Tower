package org.droidplanner.android.activities;

import java.util.List;

import org.droidplanner.R;
import org.droidplanner.android.activities.helpers.SuperUI;
import org.droidplanner.android.activities.interfaces.OnEditorInteraction;
import org.droidplanner.core.drone.Drone;
import org.droidplanner.core.drone.DroneInterfaces.DroneEventsType;
import org.droidplanner.android.fragments.EditorListFragment;
import org.droidplanner.android.fragments.EditorMapFragment;
import org.droidplanner.android.fragments.EditorToolsFragment;
import org.droidplanner.android.fragments.EditorToolsFragment.EditorTools;
import org.droidplanner.android.fragments.EditorToolsFragment.OnEditorToolSelected;
import org.droidplanner.android.fragments.helpers.GestureMapFragment;
import org.droidplanner.android.fragments.helpers.GestureMapFragment.OnPathFinishedListener;
import org.droidplanner.android.fragments.helpers.MapProjection;
import org.droidplanner.android.fragments.mission.MissionDetailFragment.OnWayPointTypeChangeListener;
import org.droidplanner.android.graphic.DroneHelper;
import org.droidplanner.android.graphic.managers.MissionItemDetailManager;
import org.droidplanner.core.helpers.coordinates.Coord2D;
import org.droidplanner.core.mission.Mission;
import org.droidplanner.core.mission.MissionItem;

import android.app.ActionBar;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.NavUtils;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;

public class EditorActivity extends SuperUI implements OnPathFinishedListener,
		OnEditorToolSelected, OnWayPointTypeChangeListener,
		OnEditorInteraction, Callback {

	private EditorMapFragment planningMapFragment;
	private GestureMapFragment gestureMapFragment;
	private Mission mission;
	private EditorToolsFragment editorToolsFragment;
	public FragmentManager fragmentManager;
	private EditorListFragment missionListFragment;
	private TextView infoView;
	
	public MissionItemDetailManager itemDetail;

    private ActionMode contextualActionBar;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_editor);

		ActionBar actionBar = getActionBar();
        if(actionBar != null)
		    actionBar.setDisplayHomeAsUpEnabled(true);

		fragmentManager = getSupportFragmentManager();

		planningMapFragment = ((EditorMapFragment) fragmentManager
				.findFragmentById(R.id.mapFragment));
		gestureMapFragment = ((GestureMapFragment) fragmentManager
				.findFragmentById(R.id.gestureMapFragment));
		editorToolsFragment = (EditorToolsFragment) fragmentManager
				.findFragmentById(R.id.editorToolsFragment);
		missionListFragment = (EditorListFragment) fragmentManager
				.findFragmentById(R.id.missionFragment1);
		infoView = (TextView) findViewById(R.id.editorInfoWindow);

        View detailView = findViewById(R.id.containerItemDetail);
        
        itemDetail = new MissionItemDetailManager(detailView,fragmentManager);

		mission = drone.mission;
		gestureMapFragment.setOnPathFinishedListener(this);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		updateMapPadding();
	}

	private void updateMapPadding() {
		int topPadding = infoView.getBottom();
		int rightPadding = 0,bottomPadding = 0;
		if (mission.getItems().size()>0) {
			rightPadding = editorToolsFragment.getView().getRight();
			bottomPadding = missionListFragment.getView().getHeight();
		}
		planningMapFragment.mMap.setPadding(rightPadding, topPadding, 0, bottomPadding);
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		planningMapFragment.saveCameraPosition();
	}

	@Override
	public void onDroneEvent(DroneEventsType event, Drone drone) {
		super.onDroneEvent(event, drone);
		switch (event) {
		case MISSION_UPDATE:
			// Remove detail window if item is removed
			itemDetail.removeDetailIfItemIsRemoved(drone);
			break;
		default:
			break;
		}
	}

	

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			planningMapFragment.saveCameraPosition();
			NavUtils.navigateUpFromSameTask(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onMapClick(LatLng point) {
        //If an mission item is selected, unselect it.
        mission.clearSelection();
        itemDetail.removeItemDetail();
        notifySelectionChanged();

		switch (getTool()) {
		case MARKER:
			mission.addWaypoint(DroneHelper.LatLngToCoord(point));
			break;
		case DRAW:
			break;
		case POLY:
			break;
		case TRASH:
			break;
		case NONE:
			break;
		}
	}

	public EditorTools getTool() {
		return editorToolsFragment.getTool();
	}

	@Override
	public void editorToolChanged(EditorTools tools) {
		itemDetail.removeItemDetail();
		mission.clearSelection();
		notifySelectionChanged();

		switch (tools) {
		case DRAW:
		case POLY:
			Toast.makeText(this,R.string.draw_the_survey_region, Toast.LENGTH_SHORT).show();
			gestureMapFragment.enableGestureDetection();
			break;
		case MARKER:
		case TRASH:
		case NONE:
			gestureMapFragment.disableGestureDetection();
			break;
		}
	}

	@Override
	public void onPathFinished(List<Coord2D> path) {
		List<Coord2D> points = MapProjection.projectPathIntoMap(path,
				planningMapFragment.mMap);
		switch (getTool()) {
		case DRAW:
			drone.mission.addWaypoints(points);
			break;
		case POLY:
			if (path.size()>2) {
				drone.mission.addSurveyPolygon(points);				
			}else{
				editorToolsFragment.setTool(EditorTools.POLY);
				return;
			}
			break;
		default:
			break;
		}
		editorToolsFragment.setTool(EditorTools.NONE);
	}

	@Override
	public void onWaypointTypeChanged(MissionItem newItem, MissionItem oldItem) {
		mission.replace(oldItem, newItem);
		itemDetail.showItemDetail(newItem);
	}

	private static final int MENU_DELETE = 1;
	private static final int MENU_REVERSE = 2;

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		switch(item.getItemId()){
		case MENU_DELETE:
			mission.removeWaypoints(mission.getSelected());
			notifySelectionChanged();
			mode.finish();
			return true;
		case MENU_REVERSE:
			mission.reverse();
			notifySelectionChanged();
			return true;
		default:
			return false;
		}
	}

	@Override
	public boolean onCreateActionMode(ActionMode arg0, Menu menu) {
		menu.add(0, MENU_DELETE, 0, "Delete");
		menu.add(0, MENU_REVERSE, 0, "Reverse");
		editorToolsFragment.getView().setVisibility(View.INVISIBLE);
		return true;
	}

	@Override
	public void onDestroyActionMode(ActionMode arg0) {
		missionListFragment.updateChoiceMode(ListView.CHOICE_MODE_SINGLE);
		mission.clearSelection();
		notifySelectionChanged();
		contextualActionBar = null;
		editorToolsFragment.getView().setVisibility(View.VISIBLE);
	}

	@Override
	public boolean onPrepareActionMode(ActionMode arg0, Menu arg1) {
		return false;
	}

	@Override
	public boolean onItemLongClick(MissionItem item) {
		if (contextualActionBar != null) {
			if (mission.selectionContains(item)) {
				mission.clearSelection();
			} else {
				mission.clearSelection();
				mission.addToSelection(mission.getItems());
			}
			notifySelectionChanged();
		} else {
			itemDetail.removeItemDetail();
			editorToolsFragment.setTool(EditorTools.NONE);
			missionListFragment.updateChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
			contextualActionBar = startActionMode(this);
			mission.clearSelection();
			mission.addToSelection(item);
			notifySelectionChanged();
		}
		return true;
	}

	@Override
	public void onItemClick(MissionItem item) {
		switch (editorToolsFragment.getTool()) {
		default:
			if (contextualActionBar != null) {
				if (mission.selectionContains(item)) {
					mission.removeItemFromSelection(item);
				} else {
					mission.addToSelection(item);
				}
			} else {
				if (mission.selectionContains(item)) {
					mission.clearSelection();
					itemDetail.removeItemDetail();
				} else {
					editorToolsFragment.setTool(EditorTools.NONE);
					mission.setSelectionTo(item);
					itemDetail.showItemDetail(item);
				}
			}
			break;
		case TRASH:
			mission.removeWaypoint(item);
			mission.clearSelection();
			if (mission.getItems().size() <= 0) {
				editorToolsFragment.setTool(EditorTools.NONE);
			}
			break;
		}
		notifySelectionChanged();
	}

	private void notifySelectionChanged() {
        List<MissionItem> selectedItems = mission.getSelected();
        missionListFragment.updateMissionItemSelection(selectedItems);

		if (selectedItems.size() == 0) {
			missionListFragment.setArrowsVisibility(false);
		} else {
			missionListFragment.setArrowsVisibility(true);
		}
		planningMapFragment.manager.update();
	}

	@Override
	public void onListVisibilityChanged() {
		updateMapPadding();
	}

}
