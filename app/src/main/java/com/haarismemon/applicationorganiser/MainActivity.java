package com.haarismemon.applicationorganiser;

import android.app.DialogFragment;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.design.widget.Snackbar;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.haarismemon.applicationorganiser.adapter.ApplicationListRecyclerAdapter;
import com.haarismemon.applicationorganiser.database.DataSource;
import com.haarismemon.applicationorganiser.database.InternshipTable;
import com.haarismemon.applicationorganiser.listener.FilterDialogOnClickListener;
import com.haarismemon.applicationorganiser.listener.MyOnQueryTextListener;
import com.haarismemon.applicationorganiser.model.ApplicationStage;
import com.haarismemon.applicationorganiser.model.FilterType;
import com.haarismemon.applicationorganiser.model.Internship;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * This class represents the activity which displays the list of all Internships
 * @author HaarisMemon
 */
public class MainActivity extends AppCompatActivity {

    private DataSource mDataSource;
    public ActionMode actionMode;
    private ActionMode.Callback actionModeCallback;
    public MenuItem prioritiseItem;
    public MenuItem deprioritiseItem;

    private List<Internship> internships;
    private List<Internship> selectedInternships;
    private List<Internship> deletedInternships;
    private List<Internship> internshipsBeforeDeletion;

    private Map<FilterType, List<Integer>> filterSelectedItemsIndexes;
    private boolean isFilterPriority = false;

    Set<FilterType> filtersCurrentlyApplied;

    /**
     * RecyclerAdapter of RecyclerView for internships in the activity
     */
    ApplicationListRecyclerAdapter applicationListRecyclerAdapter;

    public static final String SOURCE = "SOURCE";
    public static final String FILTER_LIST = "FILTER_LIST";
    public static final String FILTER_TYPE = "FILTER_TYPE";
    public static final String CHECKED_ITEMS = "CHECKED_ITEMS";

    //used to check if currently in selection mode (whether any internships has been selected)
    public boolean isSelectionMode = false;
    private boolean isAllSelected = false;

    @BindView(R.id.toolbar) Toolbar toolbar;
    @BindView(R.id.recyclerView) RecyclerView recyclerView;

    @BindView(R.id.drawerLayout) DrawerLayout mDrawerLayout;
    @BindView(R.id.filterDrawer) LinearLayout filterDrawer;
    @BindView(R.id.roleSelect) TextView roleSelect;
    @BindView(R.id.lengthSelect) TextView lengthSelect;
    @BindView(R.id.locationSelect) TextView locationSelect;
    @BindView(R.id.salarySelect) TextView salarySelect;
    @BindView(R.id.stageSelect) TextView stageSelect;
    @BindView(R.id.statusSelect) TextView statusSelect;
    @BindView(R.id.prioritySwitch) Switch prioritySwitch;
    MenuItem orderItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        setSupportActionBar(toolbar);

        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        mDrawerLayout.setFocusableInTouchMode(false);

        mDataSource = new DataSource(this);
        mDataSource.open();
        mDataSource.seedDatbase();

        //ArrayList of all internships in the database
        internships = mDataSource.getAllInternship();

        filtersCurrentlyApplied = new HashSet<>();

        setUpFilterPanel();

        displayMessageIfNoInternships();

        //list to track which internships have been selected
        selectedInternships = new ArrayList<>();
        //list to store list of internships before deletion
        internshipsBeforeDeletion = new ArrayList<>();

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.setFocusable(false);
        //give the recycler adapter the list of all internships
        applicationListRecyclerAdapter = new ApplicationListRecyclerAdapter(this, internships, selectedInternships);
        //set the adapter to the recycler view
        recyclerView.setAdapter(applicationListRecyclerAdapter);

        //sets what is displayed in actionbar in action mode
        actionModeCallback = new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                actionMode.setTitle("0 internships selected");

                MenuInflater inflater = actionMode.getMenuInflater();
                inflater.inflate(R.menu.main_action_mode_menu, menu);

                prioritiseItem = menu.findItem(R.id.action_mode_prioritise);
                deprioritiseItem = menu.findItem(R.id.action_mode_deprioritise);

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    //when the delete action button is pressed in action mode
                    case R.id.action_mode_delete:
                        //delete all selected internships
                        deleteSelectedInternships();
                        //exit action mode
                        switchActionMode(false);

                        Snackbar.make(findViewById(R.id.drawerLayout),
                                R.string.deleted_snackbar_string, Snackbar.LENGTH_LONG)
                                .setAction(R.string.undo_snackbar_string, new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        for(Internship internship : deletedInternships) {
                                            internship.setSelected(false);
                                            mDataSource.recreateInternship(internship);
                                        }

                                        internships = internshipsBeforeDeletion;
                                        applicationListRecyclerAdapter.internshipsList = internshipsBeforeDeletion;

                                        applicationListRecyclerAdapter.notifyDataSetChanged();

                                        deletedInternships.clear();
                                    }
                                })
                                .show();

                        return true;

                    //when the priority action button is pressed in action mode
                    case R.id.action_mode_prioritise:
                        //prioritise all selected internships
                        prioritiseSelectedInternships();
                        //exit action mode
                        switchActionMode(false);
                        return true;

                    //when the priority action button is pressed in action mode
                    case R.id.action_mode_deprioritise:
                        //prioritise all selected internships
                        deprioritiseSelectedInternships();
                        //exit action mode
                        switchActionMode(false);
                        return true;

                    case R.id.action_mode_select_all:
                        selectAllInternships();
                        return true;

                }

                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                isSelectionMode = false;
                isAllSelected = false;
                //unselect all selected internships
                unselectSelectedInternships();
                //update the recycler view
                applicationListRecyclerAdapter.notifyDataSetChanged();
            }
        };

    }

    @Override
    protected void onPause() {
        super.onPause();
        mDataSource.close();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mDataSource.open();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //add search action button to action bar
        getMenuInflater().inflate(R.menu.main_menu, menu);

        searchMenuActionSetup(menu);

        orderItem = menu.findItem(R.id.action_sort_order);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case(R.id.action_sort_order):
                toggleOrderItemAscendingOrDescending();
                applicationListRecyclerAdapter.reverseOrder();
                return true;

            case(R.id.action_sort_modified_date):
                changeSort(InternshipTable.COLUMN_MODIFIED_ON, false, item);
                return true;

            case(R.id.action_sort_created_date):
                changeSort(InternshipTable.COLUMN_CREATED_ON, false, item);
                return true;

            case(R.id.action_sort_company_name):
                changeSort(InternshipTable.COLUMN_COMPANY_NAME, true, item);
                return true;

            case(R.id.action_sort_role):
                changeSort(InternshipTable.COLUMN_ROLE, true, item);
                return true;

            case(R.id.action_sort_salary):
                changeSort(InternshipTable.COLUMN_SALARY, false, item);
                return true;

            case(R.id.action_filter_internships):
                if(mDrawerLayout.isDrawerOpen(filterDrawer)) mDrawerLayout.closeDrawer(filterDrawer);
                else mDrawerLayout.openDrawer(filterDrawer);

                return true;

        }

        return super.onOptionsItemSelected(item);
    }

    private void setUpFilterPanel() {
        filterSelectedItemsIndexes = new HashMap<>();

        //add all filter types to the map
        for(FilterType filterType : FilterType.values()) {
            filterSelectedItemsIndexes.put(filterType, null);
        }

        roleSelect.setOnClickListener(new FilterDialogOnClickListener(getFragmentManager(),
                mDataSource.getAllRoles(), filterSelectedItemsIndexes, FilterType.ROLE));

        lengthSelect.setOnClickListener(new FilterDialogOnClickListener(getFragmentManager(),
                mDataSource.getAllLengths(), filterSelectedItemsIndexes, FilterType.LENGTH));

        locationSelect.setOnClickListener(new FilterDialogOnClickListener(getFragmentManager(),
                mDataSource.getAllLocations(), filterSelectedItemsIndexes, FilterType.LOCATION));

        //TODO Change salary to a number slider instead of a spinner

        List<Integer> salary = mDataSource.getAllSalary();
        List<String> stringSalary = new ArrayList<>();
        for(Integer salaryInteger : salary) {
            stringSalary.add(salaryInteger.toString());
        }
        salarySelect.setOnClickListener(new FilterDialogOnClickListener(getFragmentManager(),
                stringSalary, filterSelectedItemsIndexes, FilterType.SALARY));

        stageSelect.setOnClickListener(new FilterDialogOnClickListener(getFragmentManager(),
                mDataSource.getAllStageNames(), filterSelectedItemsIndexes, FilterType.STAGE));

        prioritySwitch.setChecked(false);

        statusSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bundle bundle = new Bundle();
                List<Integer> selectedItemsIndexes = filterSelectedItemsIndexes.get(FilterType.STATUS);

                //selectedItemsIndexes is null when nothing initially has been selected
                if(selectedItemsIndexes != null) {
                    boolean[] checkedItems = new boolean[ApplicationStage.Status.values().length];

                    //converts the indexes into boolean array of all items with value true if index in list
                    for (Integer selectedItemIndex : selectedItemsIndexes) {
                        checkedItems[selectedItemIndex] = true;
                    }

                    bundle.putBooleanArray(CHECKED_ITEMS, checkedItems);
                }

                DialogFragment dialogFragment = new StatusFilterDialogFragment();
                dialogFragment.setArguments(bundle);
                dialogFragment.show(getFragmentManager(), "Status Filter Dialog");
            }
        });

        updateFilterPanel();
    }

    /**
     * Stores the selected item's indexes for the particular filter type.
     * @param filterType the filter type that the items selected correspond to
     * @param selectedItemIndexes list of integer indexes of the items selected
     */
    public void onUserSelectValue(FilterType filterType, List<Integer> selectedItemIndexes) {
        if(selectedItemIndexes.isEmpty()) {
            selectedItemIndexes = null;
            filtersCurrentlyApplied.remove(filterType);
        } else {
            filtersCurrentlyApplied.add(filterType);
        }

        filterSelectedItemsIndexes.put(filterType, selectedItemIndexes);

        updateFilterPanel();
    }

    private void updateFilterPanel() {
        updateFilterSelectText(FilterType.ROLE, roleSelect);
        updateFilterSelectText(FilterType.LENGTH, lengthSelect);
        updateFilterSelectText(FilterType.LOCATION, locationSelect);
        updateFilterSelectText(FilterType.SALARY, salarySelect);
        updateFilterSelectText(FilterType.STAGE, stageSelect);

        if(filterSelectedItemsIndexes.get(FilterType.STATUS) != null &&
                filterSelectedItemsIndexes.get(FilterType.STATUS).size() == 1) {

            ApplicationStage.Status status = getAllStatusFromIndex(
                    filterSelectedItemsIndexes.get(FilterType.STATUS)).get(0);
            statusSelect.setText(status.toString());

            statusSelect.setCompoundDrawablesWithIntrinsicBounds(getResources().getIdentifier("ic_status_" + status.getIconNameText(), "drawable", getPackageName()), 0, 0, 0);
            statusSelect.setCompoundDrawablePadding(16);

        } else {
            updateFilterSelectText(FilterType.STATUS, statusSelect);
            statusSelect.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    private void updateFilterSelectText(FilterType filterType, TextView selectText) {
        List<Integer> selectedItems = filterSelectedItemsIndexes.get(filterType);
        if(filtersCurrentlyApplied.contains(filterType)) {
            selectText.setText(selectedItems.size() + " selected");
        } else if(filtersCurrentlyApplied.isEmpty()) {
            selectText.setText("All " + filterType.getTextPlural());
        } else {
            selectText.setText("None selected");
        }
    }

    public void resetAllFilter(View view) {
        mDrawerLayout.closeDrawer(filterDrawer);

        new AlertDialog.Builder(this)
                .setTitle(getResources().getString(R.string.areYouSureDialogTitle))
                .setMessage("All selections will be cleared")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        //add all filter types to the map
                        for(FilterType filterType : FilterType.values()) {
                            filterSelectedItemsIndexes.put(filterType, null);
                        }

                        prioritySwitch.setChecked(false);
                        isFilterPriority = false;

                        filtersCurrentlyApplied.clear();

                        applicationListRecyclerAdapter.internshipsList = internships;
                        applicationListRecyclerAdapter.notifyDataSetChanged();

                        updateFilterPanel();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    public void applyFilter(View view) {
        List<String> selectedRoles = getAllItemsFromIndex(mDataSource.getAllRoles(),
                filterSelectedItemsIndexes.get(FilterType.ROLE));

        List<String> selectedLengths = getAllItemsFromIndex(mDataSource.getAllLengths(),
                filterSelectedItemsIndexes.get(FilterType.LENGTH));

        List<String> selectedLocations = getAllItemsFromIndex(mDataSource.getAllLocations(),
                filterSelectedItemsIndexes.get(FilterType.LOCATION));

        //TODO FIX SALARY

        List<String> selectedStages = getAllItemsFromIndex(mDataSource.getAllStageNames(),
                filterSelectedItemsIndexes.get(FilterType.STAGE));

        List<ApplicationStage.Status> selectedStatus = getAllStatusFromIndex(
                filterSelectedItemsIndexes.get(FilterType.STATUS));

        if(prioritySwitch.isChecked()) isFilterPriority = true;
        else isFilterPriority = false;

        applicationListRecyclerAdapter.internshipsList = filterInternships(selectedRoles,
                selectedLengths, selectedLocations, null, selectedStages,
                selectedStatus);

        applicationListRecyclerAdapter.notifyDataSetChanged();

        mDrawerLayout.closeDrawer(filterDrawer);
    }

    private List<String> getAllItemsFromIndex(List<String> allItems, List<Integer> selectedIndexes) {
        if(selectedIndexes != null) {
            List<String> result = new ArrayList<>();
            for (int i = 0; i < allItems.size(); i++) {
                if (selectedIndexes.contains(i)) {
                    result.add(allItems.get(i));
                }
            }

            return result;
        }
        else return null;
    }

    private List<ApplicationStage.Status> getAllStatusFromIndex(List<Integer> selectedIndexes) {
        ApplicationStage.Status[] statusList = ApplicationStage.Status.values();

        if(selectedIndexes != null) {
            List<ApplicationStage.Status> result = new ArrayList<>();
            for (int i = 0; i < statusList.length; i++) {
                if (selectedIndexes.contains(i)) {
                    result.add(statusList[i]);
                }
            }

            return result;
        }
        else return null;
    }

    private void changeSort(String sortByField, boolean isAscending, MenuItem currentSelectedSortItem) {
        if(currentSelectedSortItem.isChecked()) {
            toggleOrderItemAscendingOrDescending();
            applicationListRecyclerAdapter.reverseOrder();
        } else {
            setOrderItemToAscendingOrDescending(isAscending);
            applicationListRecyclerAdapter.sortInternships(sortByField);
        }

        currentSelectedSortItem.setChecked(true);
    }

    private void setOrderItemToAscendingOrDescending(boolean isAscending) {
        if(isAscending) {
            orderItem.setTitle("Ascending Order");
            orderItem.setIcon(R.drawable.ic_arrow_upward_black_24dp);
        } else {
            orderItem.setTitle("Descending Order");
            orderItem.setIcon(R.drawable.ic_arrow_downward_black_24dp);
        }
    }

    private void toggleOrderItemAscendingOrDescending() {
        if(orderItem.getTitle().toString().toLowerCase().contains("ascending")) {
            orderItem.setTitle("Descending Order");
            orderItem.setIcon(R.drawable.ic_arrow_downward_black_24dp);
        } else {
            orderItem.setTitle("Ascending Order");
            orderItem.setIcon(R.drawable.ic_arrow_upward_black_24dp);
        }
    }

    private void searchMenuActionSetup(final Menu menu) {
        final MenuItem searchItem = menu.findItem(R.id.action_search_internships);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);

        //hides other menu items in action bar when search bar is expanded
        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {

            @Override
            public boolean onMenuItemActionExpand(final MenuItem item) {
                setItemsVisibility(menu, searchItem, false);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(final MenuItem item) {
                setItemsVisibility(menu, searchItem, true);
                return true;
            }
        });

        //set the on query text listener of the search view, and give it the adapter so that it can access the list
        searchView.setOnQueryTextListener(new MyOnQueryTextListener(applicationListRecyclerAdapter));

        //get the search manager to set the searchable.xml to the search view
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.onActionViewExpanded();

        //change the color of the caret in the search view from the default accent color to white
        AutoCompleteTextView searchTextView = (AutoCompleteTextView) searchView.findViewById(android.support.v7.appcompat.R.id.search_src_text);
        try {
            Field mCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
            mCursorDrawableRes.setAccessible(true);
            mCursorDrawableRes.set(searchTextView, R.drawable.cursor); //This sets the cursor resource ID to 0 or @null which will make it visible on white background
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setItemsVisibility(final Menu menu, final MenuItem searchMenuItem, final boolean isItemsVisible) {
        for (int i = 0; i < menu.size(); ++i) {
            MenuItem item = menu.getItem(i);
            item.setVisible(isItemsVisible);
        }

        if(isItemsVisible) invalidateOptionsMenu();
    }

    /**
     * On click method to create a new Internship
     * @param view create button that was clicked
     */
    public void goToCreateInternship(View view) {
        Intent intent = new Intent(getApplicationContext(), InternshipEditActivity.class);
        intent.putExtra(InternshipEditActivity.INTERNSHIP_EDIT_MODE, false);
        startActivity(intent);
    }

    /**
     * Turns on action mode if true passed, otherwise turns action mode off
     * @param turnOn true if to turn action mode on
     */
    public void switchActionMode(boolean turnOn) {
        //if turnOn is true, then start action mode
        if(turnOn) {
            isSelectionMode = true;
            actionMode = startSupportActionMode(actionModeCallback);
        } else {
            //else if turnOn is false, then exit action mode
            if (actionMode != null) {
                isSelectionMode = false;
                actionMode.finish();
            }
        }
    }

    //this method updates the title in the action bar in action mode, and is called every time internship selected
    public void updateActionModeCounter(int counter) {
        if(counter == 1) {
            actionMode.setTitle(counter + " internship selected");
        } else {
            actionMode.setTitle(counter + " internships selected");
        }
    }

    //displays message to inform user to add their first internship if internship list is empty
    public void displayMessageIfNoInternships() {
        if(internships.isEmpty()) {
            RelativeLayout mainRelativeLayout = (RelativeLayout) findViewById(R.id.mainRelativeLayout);

            TextView messageWhenEmpty = new TextView(this);
            messageWhenEmpty.setText(getResources().getString(R.string.addFirstInternship));

            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT));
            layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
            messageWhenEmpty.setLayoutParams(layoutParams);

            mainRelativeLayout.addView(messageWhenEmpty);
        }
    }

    //unselects all the selected internships
    private void unselectSelectedInternships() {
        for(Internship internship : selectedInternships) {
            internship.setSelected(false);
        }

        applicationListRecyclerAdapter.notifyDataSetChanged();

        selectedInternships.clear();
    }

    //deletes all selected internships
    private void deleteSelectedInternships() {
        internshipsBeforeDeletion = new ArrayList<>(internships);

        //for all selected internships
        for(Internship deleteInternship : selectedInternships) {
            //remove from list
            internships.remove(deleteInternship);
            //delete from database
            mDataSource.deleteInternship(deleteInternship.getInternshipID());
        }
        //update the RecyclerView through the adapter
        applicationListRecyclerAdapter.internshipsList = internships;
        applicationListRecyclerAdapter.notifyDataSetChanged();

        deletedInternships = new ArrayList<>(selectedInternships);

        //empty the map holding the selected internships
        selectedInternships.clear();

        displayMessageIfNoInternships();
    }

    //prioritises all selected internships
    private void prioritiseSelectedInternships() {
        //for all selected internships
        for(Internship internship : selectedInternships) {
            internship.setSelected(false);
            internship.setPriority(true);

            mDataSource.updateInternshipPriority(internship);
        }
        //empty the map holding the selected internships
        selectedInternships.clear();
    }

    //deprioritises all selected internships
    private void deprioritiseSelectedInternships() {
        //for all selected internships
        for(Internship internship : selectedInternships) {
            internship.setSelected(false);
            internship.setPriority(false);

            mDataSource.updateInternshipPriority(internship);
        }
        //empty the map holding the selected internships
        selectedInternships.clear();
    }

    private void selectAllInternships() {
        if(!isAllSelected) {
            applicationListRecyclerAdapter.selectAllInternships();
            isAllSelected = true;
        } else {
            applicationListRecyclerAdapter.deselectAllInternships();
            isAllSelected = false;
        }
    }

    @Override
    public void onBackPressed() {
        if(mDrawerLayout.isDrawerOpen(filterDrawer))
            mDrawerLayout.closeDrawer(filterDrawer);
        else super.onBackPressed();
    }

    private List<Internship> filterInternships(List<String> roles,
                                   List<String> lengths,
                                   List<String> locations,
                                   List<String> salaries,
                                   List<String> stages,
                                   List<ApplicationStage.Status> status) {

        List<Internship> result = new ArrayList<>();

        for (Internship internship : internships) {
            if (isFilterPriority && !internship.isPriority()) {
                continue;
            }

            //if no filters have been selected then no filters are applied, so show all internships
            if(!filtersCurrentlyApplied.isEmpty()) {
                Internship returnedInternship = matchInternship(internship, roles, lengths, locations, salaries, stages, status);
                if (returnedInternship != null) result.add(returnedInternship);
            } else {
                result.add(internship);
            }
        }

        return result;
    }

    private Internship matchInternship(Internship internship,
                                       List<String> roles,
                                       List<String> lengths,
                                       List<String> locations,
                                       List<String> salaries,
                                       List<String> stages,
                                       List<ApplicationStage.Status> statusList) {
        if(statusList != null) {
            boolean statusMatched = false;
            for (ApplicationStage.Status status : statusList) {
                if (internship.getCurrentStage().getCurrentStatus().equals(status)) {
                    statusMatched = true;
                    break;
                }
            }
            if(!statusMatched) return null;
        }

        if(roles != null) {
            boolean roleMatched = false;
            for (String role : roles) {
                if (internship.getRole().equals(role)) {
                    roleMatched = true;
                    break;
                }
            }
            if(!roleMatched) return null;
        }

        if(lengths != null) {
            boolean lengthMatched = false;
            for (String length : lengths) {
                if (internship.getLength() != null && internship.getLength().equals(length)) {
                    lengthMatched = true;
                    break;
                }
            }
            if(!lengthMatched) return null;
        }

        if(locations != null) {
            boolean locationMatched = false;
            for (String location : locations) {
                if (internship.getLocation() != null && internship.getLocation().equals(location)) {
                    locationMatched = true;
                    break;
                }
            }
            if(!locationMatched) return null;
        }

        if(salaries != null) {
            boolean salaryMatched = false;
            for (String salary : salaries) {
                if (internship.getSalary() == Integer.parseInt(salary)) {
                    salaryMatched = true;
                    break;
                }
            }
            if(!salaryMatched) return null;
        }

        if(stages != null) {
            boolean stageMatched = false;
            for (String stageName : stages) {
                for (ApplicationStage stage : internship.getApplicationStages()) {
                    if (stage.getStageName().equals(stageName)) {
                        stageMatched = true;
                        break;
                    }
                }
            }
            if(!stageMatched) return null;
        }

        return internship;
    }

    public void setInternshipList(List<Internship> internshipList) {
        internships = internshipList;
    }

}
