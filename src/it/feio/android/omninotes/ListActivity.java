/*******************************************************************************
 * Copyright 2014 Federico Iosue (federico.iosue@gmail.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package it.feio.android.omninotes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.espian.showcaseview.ShowcaseView;
import com.haarman.listviewanimations.swinginadapters.prepared.SwingBottomInAnimationAdapter;
import com.neopixl.pixlui.components.textview.TextView;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import it.feio.android.omninotes.models.Attachment;
import it.feio.android.omninotes.models.NavigationDrawerAdapter;
import it.feio.android.omninotes.models.NavDrawerTagAdapter;
import it.feio.android.omninotes.models.Note;
import it.feio.android.omninotes.models.NoteAdapter;
import it.feio.android.omninotes.models.ONStyle;
import it.feio.android.omninotes.models.Tag;
import it.feio.android.omninotes.utils.Constants;
import it.feio.android.omninotes.utils.StorageManager;
import it.feio.android.omninotes.async.DeleteNoteTask;
import it.feio.android.omninotes.async.UpdaterTask;
import it.feio.android.omninotes.db.DbHelper;
import it.feio.android.omninotes.R;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.drawable.AnimationDrawable;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.view.ActionMode;
import android.support.v7.view.ActionMode.Callback;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SearchView.OnCloseListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;

public class ListActivity extends BaseActivity {

	private static final int REQUEST_CODE_DETAIL = 1;	
	private static final int REQUEST_CODE_TAG = 2;
	
	private CharSequence mTitle;
	String[] mNavigationArray;
	TypedArray mNavigationIconsArray;
	private ActionBarDrawerToggle mDrawerToggle;
	private DrawerLayout mDrawerLayout;
	private ListView listView;
	NoteAdapter mAdapter;
	ActionMode mActionMode;
	HashSet<Note> selectedNotes = new HashSet<Note>();
	private ListView mDrawerList;
	private ListView mDrawerTagList;
	private View tagListHeader;
	private Tag candidateSelectedTag;
	private SearchView searchView;
	public MenuItem searchMenuItem;
	private TextView empyListItem;
	private AnimationDrawable jinglesAnimation; 


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_list);
				
		// Get intent, action and MIME type to handle intent-filter requests
		Intent intent = getIntent();
		if ((Intent.ACTION_SEND.equals(intent.getAction()) || Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) && intent.getType() != null) {
			handleFilter(intent);
		}
		
		// Easter egg initialization
		initEasterEgg();

		// Listview initialization
		initListView();

		String[] navigationList = getResources().getStringArray(R.array.navigation_list);
		String[] navigationListCodes = getResources().getStringArray(R.array.navigation_list_codes);
		String navigation = prefs.getString(Constants.PREF_NAVIGATION, navigationListCodes[0]);
		int index = Arrays.asList(navigationListCodes).indexOf(navigation);
		CharSequence title = "";
		// If is a traditional navigation item
		if (index >= 0 && index < navigationListCodes.length) {
			title = navigationList[index];
		} else {
			ArrayList<Tag> tags = db.getTags();
			for (Tag tag : tags) {
				if ( navigation.equals(String.valueOf(tag.getId())) )
						title = tag.getName();						
			}
		}
		setTitle(title == null ? getString(R.string.title_activity_list) : title);
	

		// Launching update task
		UpdaterTask task = new UpdaterTask(this);
		task.execute();
		
		
		// Instructions on first launch
//		showCase();
	}
	
	
	
	
	/**
	 * Starts a little animation on Mr.Jingles!
	 */
	private void initEasterEgg() {
		empyListItem = (TextView) findViewById(R.id.empty_list);
		empyListItem.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {			
				if (jinglesAnimation == null) {
					jinglesAnimation = (AnimationDrawable) empyListItem.getCompoundDrawables()[1];
					empyListItem.post(new Runnable() {
					    public void run() {
					        if ( jinglesAnimation != null ) jinglesAnimation.start();
					      }
					});
				} else {
					stopJingles();					
				}
			}
		});
	}


	private void stopJingles() {
		if(jinglesAnimation != null) {
			jinglesAnimation.stop();
			jinglesAnimation = null;
			empyListItem.setCompoundDrawablesWithIntrinsicBounds(0,
					R.animator.jingles_animation, 0, 0);
	
		}
	}



	@Override
	protected void onPause() {
		super.onPause();
		Crouton.cancelAllCroutons();
		stopJingles();
	}
	
	
	
	/**
	 * Handles third party apps requests of sharing
	 * @param intent
	 */
	private void handleFilter(Intent intent) {
		Note note = new Note();
				
		// Text title
		String title = intent.getStringExtra(Intent.EXTRA_SUBJECT);
		if (title != null) {
			note.setTitle(title);
		}
		// Text content
		String content = intent.getStringExtra(Intent.EXTRA_TEXT);
		if (content != null) {
			note.setContent(content);
		}
		// Single attachment data
		Uri uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
	    if (uri != null) {
	    	String mimeType = StorageManager.getMimeTypeInternal(this, intent.getType());
	        note.addAttachment(new Attachment(uri, mimeType));
	    }
	    // Multiple attachment data
	    ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
	    if (uris != null) {
	    	String mimeType = StorageManager.getMimeTypeInternal(this, intent.getType());	    	
	    	for (Uri uriSingle : uris) {
		        note.addAttachment(new Attachment(uriSingle, mimeType));				
			}
	    }
	    
	    // Editing activity launch
		Intent detailIntent = new Intent(this, DetailActivity.class);
		detailIntent.putExtra(Constants.INTENT_NOTE, note);
		startActivity(detailIntent);
	}


	@Override
	protected void onResume() {
		super.onResume();
		Log.v(Constants.TAG, "OnResume");
		initNotesList(getIntent());
		initNavigationDrawer();
	}
	
	

	private final class ModeCallback implements Callback {
		 
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			// Inflate the menu for the CAB
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.menu, menu);
			mActionMode = mode;
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			// Here you can make any necessary updates to the activity when
			// the CAB is removed. By default, selected items are deselected/unchecked.
			
			
	    	Iterator it = mAdapter.getSelectedItems().entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry mapEntry = (Map.Entry) it.next();
				int i = (Integer) mapEntry.getKey();
				if (mAdapter.getCount() > i && mAdapter.getItem(i) != null) {
					mAdapter.restoreDrawable(mAdapter.getItem(i), listView.getChildAt(i).findViewById(R.id.card_layout));
				}
			}

			// Clears data structures
			selectedNotes.clear();
			mAdapter.clearSelectedItems();	
			listView.clearChoices();
			
			mActionMode = null;
			Log.d(Constants.TAG, "Closed multiselection contextual menu");
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			// Here you can perform updates to the CAB due to
			// an invalidate() request
			Log.d(Constants.TAG, "CAB preparation");
			boolean notes = getResources().getStringArray(R.array.navigation_list_codes)[0].equals(navigation);
			boolean archived = getResources().getStringArray(R.array.navigation_list_codes)[1].equals(navigation);
						
			menu.findItem(R.id.menu_archive).setVisible(notes);
			menu.findItem(R.id.menu_unarchive).setVisible(archived);
			menu.findItem(R.id.menu_tag).setVisible(true);
			menu.findItem(R.id.menu_delete).setVisible(true);
			menu.findItem(R.id.menu_settings).setVisible(false);
			return true;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			// Respond to clicks on the actions in the CAB
			switch (item.getItemId()) {
				case R.id.menu_delete:
					deleteSelectedNotes();
					return true;
				case R.id.menu_archive:
					archiveSelectedNotes(true);
					mode.finish(); // Action picked, so close the CAB
					return true;
				case R.id.menu_unarchive:
					archiveSelectedNotes(false);
					mode.finish(); // Action picked, so close the CAB
					return true;
				case R.id.menu_tag:
					tagSelectedNotes();
					return true;
				default:
					return false;
			}
		}
    };
    
    
    
    /**
     * Manage check/uncheck of notes in list during multiple selection phase
     * @param view
     * @param position
     */
	private void toggleListViewItem(View view, int position) {
		Note note = mAdapter.getItem(position);
		LinearLayout v = (LinearLayout) view.findViewById(R.id.card_layout);
		if (!selectedNotes.contains(note)) {
			selectedNotes.add(note);
			mAdapter.addSelectedItem(position);
			v.setBackgroundColor(getResources().getColor(R.color.list_bg_selected));
		} else {
			selectedNotes.remove(note);
			mAdapter.removeSelectedItem(position);
			mAdapter.restoreDrawable(note, v);
		}
		if (selectedNotes.size() == 0)
			mActionMode.finish();
	}
    

	
	/**
	 * Notes list initialization. Data, actions and callback are defined here.
	 */
	private void initListView() {
			listView = (ListView) findViewById(R.id.notesList);
			
			listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
			listView.setItemsCanFocus(false);
			
			// Note long click to start CAB mode
			listView.setOnItemLongClickListener(new OnItemLongClickListener() {
				
				@Override
				public boolean onItemLongClick(AdapterView<?> arg0, View view, int position, long arg3) {
					if (mActionMode != null) {
			            return false;
			        }
	
			        // Start the CAB using the ActionMode.Callback defined above
			        startSupportActionMode(new ModeCallback());
			        toggleListViewItem(view, position);
			        setCabTitle();
			        
			        return true;
				}
			});
	
			// Note single click listener managed by the activity itself
			listView.setOnItemClickListener(new OnItemClickListener() {

				@Override
				public void onItemClick(AdapterView<?> arg0, View view,
						int position, long arg3) {// If no CAB just note editing
					if (mActionMode == null) { 
						Note note = mAdapter.getItem(position);
						editNote(note);
						return;
					}

					// If in CAB mode 
			        toggleListViewItem(view, position);
			        setCabTitle();
				}
				
			});
	}
	

	/**
	 * Initialization of compatibility navigation drawer
	 */
	private void initNavigationDrawer() {

		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

		// Sets the adapter for the MAIN navigation list view
		mDrawerList = (ListView) findViewById(R.id.drawer_nav_list);
		mNavigationArray = getResources().getStringArray(R.array.navigation_list);
		mNavigationIconsArray = getResources().obtainTypedArray(R.array.navigation_list_icons);
		mDrawerList
				.setAdapter(new NavigationDrawerAdapter(this, mNavigationArray, mNavigationIconsArray));
		
		// Sets click events
		mDrawerList.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
				String navigation = getResources().getStringArray(R.array.navigation_list_codes)[position];
				Log.d(Constants.TAG, "Selected voice " + navigation + " on navigation menu");
				selectNavigationItem(mDrawerList, position);
				updateNavigation(navigation);
				mDrawerList.setItemChecked(position, true);
				if (mDrawerTagList != null)
					mDrawerTagList.setItemChecked(0, false);  // Called to force redraw
				initNotesList(getIntent());
			}
		});

		// Sets the adapter for the TAGS navigation list view		

		// Retrieves data to fill tags list
		ArrayList<Tag> tags = db.getTags();
		
		if (tags.size() > 0) {
			mDrawerTagList = (ListView) findViewById(R.id.drawer_tag_list);
			// Inflation of header view
			LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
			if (tagListHeader == null) {
				tagListHeader = inflater.inflate(R.layout.drawer_tag_list_header, (ViewGroup) findViewById(R.id.layout_root));
				mDrawerTagList.addHeaderView(tagListHeader);
				mDrawerTagList.setHeaderDividersEnabled(true);
			}
			mDrawerTagList
					.setAdapter(new NavDrawerTagAdapter(this, tags));
			
			// Sets click events
			mDrawerTagList.setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
					Object item = mDrawerTagList.getAdapter().getItem(position);
					// Ensuring that clicked item is not the ListView header
					if (item != null) {
						Tag tag = (Tag)item;						
						String navigation = tag.getName();
						Log.d(Constants.TAG, "Selected voice " + navigation + " on navigation menu");
						selectNavigationItem(mDrawerTagList, position);
						updateNavigation(String.valueOf(tag.getId()));
						mDrawerTagList.setItemChecked(position, true);
						if (mDrawerList != null)
							mDrawerList.setItemChecked(0, false);  // Called to force redraw
						initNotesList(getIntent());
					}
				}
			});
			
			// Sets long click events
			mDrawerTagList.setOnItemLongClickListener(new OnItemLongClickListener() {
				@Override
				public boolean onItemLongClick(AdapterView<?> arg0, View view, int position, long arg3) {
					Object item = mDrawerTagList.getAdapter().getItem(position);
					// Ensuring that clicked item is not the ListView header
					if (item != null) {
						editTag((Tag)item);
					}
					return true;
				}
			});
		} else {
			if (mDrawerTagList != null) {
				mDrawerTagList.removeAllViewsInLayout();
				mDrawerTagList = null;
			}
		}

        // enable ActionBar app icon to behave as action to toggle nav drawer
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

		// ActionBarDrawerToggle± ties together the the proper interactions
		// between the sliding drawer and the action bar app icon
		mDrawerToggle = new ActionBarDrawerToggle(
			this, /* host Activity */
			mDrawerLayout, /* DrawerLayout object */
			R.drawable.ic_drawer, /* nav drawer image to replace 'Up' caret */
			R.string.drawer_open, /* "open drawer" description for accessibility */
			R.string.drawer_close /* "close drawer" description for accessibility */
		) {

			public void onDrawerClosed(View view) {
				getSupportActionBar().setTitle(mTitle);
				supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
			}

			public void onDrawerOpened(View drawerView) {
				// Stops search service
				if (searchMenuItem != null && MenuItemCompat.isActionViewExpanded(searchMenuItem))
					MenuItemCompat.collapseActionView(searchMenuItem);
				
				mTitle = getSupportActionBar().getTitle();
				getSupportActionBar().setTitle(getApplicationContext().getString(R.string.app_name));
				supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
			}
		};
		mDrawerToggle.setDrawerIndicatorEnabled(true);
		mDrawerLayout.setDrawerListener(mDrawerToggle);

		mDrawerToggle.syncState();
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		// Sync the toggle state after onRestoreInstanceState has occurred.
		if (mDrawerToggle != null)
			mDrawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		mDrawerToggle.onConfigurationChanged(newConfig);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		super.onCreateOptionsMenu(menu);

		// Setting the conditions to show determinate items in CAB
		// If the nav drawer is open, hide action items related to the content view
		boolean drawerOpen;
		if (mDrawerLayout != null) {
			drawerOpen = mDrawerLayout.isDrawerOpen(GravityCompat.START);
		} else {
			drawerOpen = false;
		}
		
		// If archived or reminders notes are shown the "add new note" item must be hidden
		String navArchived = getResources().getStringArray(R.array.navigation_list_codes)[1];
		String navReminders = getResources().getStringArray(R.array.navigation_list_codes)[2];
		boolean showAdd = !navArchived.equals(navigation) && !navReminders.equals(navigation);

		menu.findItem(R.id.menu_search).setVisible(!drawerOpen);
		menu.findItem(R.id.menu_add).setVisible(!drawerOpen && showAdd);
		menu.findItem(R.id.menu_sort).setVisible(!drawerOpen);
		menu.findItem(R.id.menu_add_tag).setVisible(drawerOpen);
		menu.findItem(R.id.menu_settings).setVisible(true);

		// Initialization of SearchView
		initSearchView(menu);
		
		// Show instructions on first launch
		showCase(Constants.PREF_INSTRUCTIONS_PREFIX + "listactivity_actions", R.id.menu_add, ShowcaseView.ITEM_ACTION_ITEM);
				
		return super.onCreateOptionsMenu(menu);
	}
	


	/**
	 * SearchView initialization.
	 * It's a little complex because it's not using SearchManager but is implementing on its own.
	 * @param menu
	 */
	private void initSearchView(final Menu menu) {
		
		// Save item as class attribute to make it collapse on drawer opening
		searchMenuItem = menu.findItem(R.id.menu_search);

		// Associate searchable configuration with the SearchView
		SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
		searchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.menu_search));
		searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
		searchView.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
		
		// Expands the widget hiding other actionbar icons
		searchView.setOnQueryTextFocusChangeListener(new OnFocusChangeListener() {			
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				Log.d(Constants.TAG, "Search focus");
				menu.findItem(R.id.menu_add).setVisible(!hasFocus);
				menu.findItem(R.id.menu_sort).setVisible(!hasFocus);
//						searchView.setIconified(!hasFocus);
			}
		});


		// Sets events on searchView closing to restore full notes list
		MenuItem menuItem = menu.findItem(R.id.menu_search);

		int currentapiVersion = android.os.Build.VERSION.SDK_INT;
		if (currentapiVersion >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			MenuItemCompat.setOnActionExpandListener(menuItem, new MenuItemCompat.OnActionExpandListener() {

				@Override
				public boolean onMenuItemActionCollapse(MenuItem item) {
					// Reinitialize notes list to all notes when search is collapsed
					Log.i(Constants.TAG, "onMenuItemActionCollapse " + item.getItemId());
					getIntent().setAction(Intent.ACTION_MAIN);
					initNotesList(getIntent());
					return true; 
				}

				@Override
				public boolean onMenuItemActionExpand(MenuItem item) {
					Log.i(Constants.TAG, "onMenuItemActionExpand " + item.getItemId());
					return true;
				}
			});
		} else {
			// Do something for phones running an SDK before froyo
			searchView.setOnCloseListener(new OnCloseListener() {

				@Override
				public boolean onClose() {
					Log.i(Constants.TAG, "mSearchView on close ");
					getIntent().setAction(Intent.ACTION_MAIN);
					initNotesList(getIntent());
					return false;
				}
			});
		}
		
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:				 
	            if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
	                mDrawerLayout.closeDrawer(GravityCompat.START);
	            } else {
	                mDrawerLayout.openDrawer(GravityCompat.START);
	            }
	            break;
			case R.id.menu_add:
				editNote(new Note());
				break;
			case R.id.menu_sort:
				sortNotes();
				break;
			case R.id.menu_add_tag:
				editTag(null);
				break;
		}
		return super.onOptionsItemSelected(item);
	}

   

	private void setCabTitle() {
		if (mActionMode == null)
			return;
		switch (selectedNotes.size()) {
			case 0:
				mActionMode.setTitle(null);
				break;
			default:
				mActionMode.setTitle(String.valueOf(selectedNotes.size()));
				break;
		}		
		
	}


	private void editNote(Note note) {
		if (note.get_id() == 0) {
			Log.d(Constants.TAG, "Adding new note");
			// if navigation is a tag it will be set into note
			try {
				int tagId = Integer.parseInt(navigation);
				note.setTag(db.getTag(tagId));
			} catch (NumberFormatException e) {}
		} else {
			Log.d(Constants.TAG, "Editing note with id: " + note.get_id());
		}

		Intent detailIntent = new Intent(this, DetailActivity.class);
		detailIntent.putExtra(Constants.INTENT_NOTE, note);
		startActivityForResult(detailIntent, REQUEST_CODE_DETAIL);
		if (prefs.getBoolean("settings_enable_animations", true)) {
			overridePendingTransition(R.animator.slide_back_right, R.animator.slide_back_left);
		}
	}
	
	
	@Override
	// Used to show a Crouton dialog after saved (or tried to) a note
	protected void onActivityResult(int requestCode, final int resultCode,
			Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		switch (requestCode) {
		case REQUEST_CODE_DETAIL:
			if (intent != null) {

				String intentMsg = intent
						.getStringExtra(Constants.INTENT_DETAIL_RESULT_MESSAGE);
				// If no message is returned nothing will be shown
				if (intentMsg != null && intentMsg.length() > 0) {
					final String message = intentMsg != null ? intent
							.getStringExtra(Constants.INTENT_DETAIL_RESULT_MESSAGE)
							: "";
					// Dialog retarded to give time to activity's views of being
					// completely initialized
					new Handler().postDelayed(new Runnable() {
						@Override
						public void run() {
							// The dialog style is choosen depending on result
							// code
							switch (resultCode) {
							case Activity.RESULT_OK:
								Crouton.makeText(mActivity, message,
										ONStyle.CONFIRM).show();
								break;
							case Activity.RESULT_FIRST_USER:
								Crouton.makeText(mActivity, message,
										ONStyle.INFO).show();
								break;
							case Activity.RESULT_CANCELED:
								Crouton.makeText(mActivity, message,
										ONStyle.ALERT).show();
								break;

							default:
								break;
							}
						}
					}, 800);
				}
			}
			break;

		case REQUEST_CODE_TAG:
			// Dialog retarded to give time to activity's views of being
			// completely initialized
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					// The dialog style is choosen depending on result code
					switch (resultCode) {
					case Activity.RESULT_OK:
						Crouton.makeText(mActivity, R.string.tag_saved,
								ONStyle.CONFIRM).show();
						break;
					case Activity.RESULT_CANCELED:
						Crouton.makeText(mActivity, R.string.tag_deleted,
								ONStyle.ALERT).show();
						break;

					default:
						break;
					}
				}
			}, 800);

			break;

		default:
			break;
		}
	}


	private void sortNotes() {
		onCreateDialog().show();
	}

	/**
	 * Creation of a dialog for choose sorting criteria
	 * 
	 * @return
	 */
	public Dialog onCreateDialog() {
		//  Two array are used, one with db columns and a corrispective with column names human readables
		final String[] arrayDb = getResources().getStringArray(R.array.sortable_columns);
		final String[] arrayDialog = getResources().getStringArray(R.array.sortable_columns_human_readable);
		
		// Dialog and events creation
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.select_sorting_column).setItems(arrayDialog,
				new DialogInterface.OnClickListener() {

					// On choosing the new criteria will be saved into preferences and listview redesigned
					public void onClick(DialogInterface dialog, int which) {
						prefs.edit().putString(Constants.PREF_SORTING_COLUMN, (String) arrayDb[which])
								.commit();
						initNotesList(getIntent());
					}
				});
		return builder.create();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		if (intent.getAction() == null) {
			intent.setAction(Constants.ACTION_START_APP);
		}
		setIntent(intent);
		Log.d(Constants.TAG, "onNewIntent");
		super.onNewIntent(intent);
	}
	
	/**
	 * Notes list adapter initialization and association to view
	 */
	private void initNotesList(Intent intent) {

		Log.v(Constants.TAG, "initNotesList: intent action " + intent.getAction());
		
		List<Note> notes;
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			notes = handleIntent(intent);
			intent.setAction(null);
		} else {
			DbHelper db = new DbHelper(getApplicationContext());
			notes = db.getAllNotes(true);
		}
		mAdapter = new NoteAdapter(getApplicationContext(), notes);

		if (prefs.getBoolean("settings_enable_animations", true)) {
		    SwingBottomInAnimationAdapter swingInAnimationAdapter = new SwingBottomInAnimationAdapter(mAdapter);
		    // Assign the ListView to the AnimationAdapter and vice versa
		    swingInAnimationAdapter.setAbsListView(listView);
		    listView.setAdapter(swingInAnimationAdapter);
		} else {
			listView.setAdapter(mAdapter);
		}
		
		if (notes.size() == 0)
			listView.setEmptyView(findViewById(R.id.empty_list));
		
	}
	
	

	/**
	 * Handle search intent
	 * @param intent
	 * @return
	 */
	private List<Note> handleIntent(Intent intent) {
		List<Note> notesList = new ArrayList<Note>();
		// Get the intent, verify the action and get the query
		String pattern = intent.getStringExtra(SearchManager.QUERY);
		Log.d(Constants.TAG, "Search launched");
		DbHelper db = new DbHelper(this);
		notesList = db.getMatchingNotes(pattern);
		Log.d(Constants.TAG, "Found " + notesList.size() + " elements matching");
		searchView.clearFocus();
		return notesList;

	}


	/** Swaps fragments in the main content view 
	 * @param list */
	private void selectNavigationItem(ListView list, int position) {
		Object itemSelected = list.getItemAtPosition(position);
		if (itemSelected.getClass().isAssignableFrom(String.class)) {
			mTitle = (CharSequence)itemSelected;	
		// Is a tag
		} else {
			mTitle = ((Tag)itemSelected).getName();
		}
		mDrawerLayout.closeDrawer(GravityCompat.START);
	}

	/**
	 * Batch note deletion
	 */
	public void deleteSelectedNotes() {

		// Confirm dialog creation
		final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
		alertDialogBuilder.setMessage(R.string.delete_note_confirmation)
				.setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int id) {
						for (Note note : selectedNotes) {
							deleteNote(note);
						}
						// Refresh view
						ListView l = (ListView) findViewById(R.id.notesList);
						l.invalidateViews();					
						
						// If list is empty again Mr Jingles will appear again
						if (l.getCount() == 0)
							listView.setEmptyView(findViewById(R.id.empty_list));

						// Clears data structures
						selectedNotes.clear();
						mAdapter.clearSelectedItems();	
						listView.clearChoices();
						
						// Advice to user
						Crouton.makeText(mActivity, R.string.note_deleted, ONStyle.ALERT).show();
						mActionMode.finish(); // Action picked, so close the CAB
					}
				}).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int id) {

						// Clears data structures
						selectedNotes.clear();
						mAdapter.clearSelectedItems();	
						listView.clearChoices();
						
						mActionMode.finish(); // Action picked, so close the CAB
					}
				});
		AlertDialog alertDialog = alertDialogBuilder.create();
		alertDialog.show();

	}
	

	/**
	 * Single note deletion
	 * @param note Note to be deleted
	 */
	@SuppressLint("NewApi")
	protected void deleteNote(Note note) {
		
		// Saving changes to the note
		DeleteNoteTask deleteNoteTask = new DeleteNoteTask(getApplicationContext());
		// Forceing parallel execution disabled by default
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			deleteNoteTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, note);
		} else {
			deleteNoteTask.execute(note);
		}

		// Update adapter content
		mAdapter.remove(note);

		// Informs about update
		Log.d(Constants.TAG, "Deleted note with id '" + note.get_id() + "'");
	}


	/**
	 * Batch note archiviation
	 */
	public void archiveSelectedNotes(boolean archive) {
		String archivedStatus = archive ? getResources().getText(R.string.note_archived).toString()
				: getResources().getText(R.string.note_unarchived).toString();
		for (Note note : selectedNotes) {
			// Deleting note using DbHelper
			DbHelper db = new DbHelper(this);
			note.setArchived(archive);
			db.updateNote(note);

			// Update adapter content
			mAdapter.remove(note);

			// Informs the user about update
			Log.d(Constants.TAG, "Note with id '" + note.get_id() + "' " + archivedStatus);
		}

		// Clears data structures
		selectedNotes.clear();
		mAdapter.clearSelectedItems();	
		listView.clearChoices();

		// Refresh view
		((ListView) findViewById(R.id.notesList)).invalidateViews();
		// Advice to user
		Crouton.makeText(mActivity, archivedStatus, ONStyle.INFO).show();
	}
	
	
	/**
	 * Tags addition and editing
	 * @param tag
	 */
	private void editTag(Tag tag){
		Intent tagIntent = new Intent(this, TagActivity.class);
		tagIntent.putExtra(Constants.INTENT_TAG, tag);
		startActivityForResult(tagIntent, REQUEST_CODE_TAG);
	}
	
	
	/**
	 * Tag selected notes
	 */
	private void tagSelectedNotes() {
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mActivity);

		// Retrieves all available tags
		final ArrayList<Tag> tags = db.getTags();
		
		// If there is no tag a message will be shown
		if (tags.size() == 0) {
			Crouton.makeText(mActivity, R.string.no_tags_created, ONStyle.WARN).show();
			return;
		}

		// If just one note is selected its tag will be set as pre-selected
		if (selectedNotes.size() == 1) {
			for (Note note : selectedNotes) {
				if (note.getTag() != null && note.getTag().getId() != 0)
					candidateSelectedTag = note.getTag();
				else 
					candidateSelectedTag = tags.get(0);
			}
		} else {
			candidateSelectedTag = tags.get(0);
		}		
		
		// Choosing the pre-selected item in the dialog list
		ArrayList<String> tagsNames = new ArrayList<String>();
		int selectedIndex = 0;		
		for (int i = 0; i < tags.size(); i++) {
			Tag tag = tags.get(i);
			tagsNames.add(tag.getName());
			if (candidateSelectedTag.getId() == tag.getId()){
				selectedIndex = i;
			}
		}

		// A single choice dialog will be displayed
		final String[] navigationListCodes = getResources().getStringArray(R.array.navigation_list_codes);
		final String navigation = prefs.getString(Constants.PREF_NAVIGATION, navigationListCodes[0]);
		
		final String[] array = tagsNames.toArray(new String[tagsNames.size()]);
		alertDialogBuilder.setTitle(R.string.tag_as)
//							.setSingleChoiceItems(array, selectedIndex, new DialogInterface.OnClickListener() {
							.setAdapter(new NavDrawerTagAdapter(mActivity, tags), new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									candidateSelectedTag = tags.get(which);
									for (Note note : selectedNotes) {
										// Update adapter content if actual navigation is the tag
										// associated with actually cycled note
										if (!Arrays.asList(navigationListCodes).contains(navigation)
												&& !navigation.equals(candidateSelectedTag.getId())) {
											mAdapter.remove(note);
										}
										note.setTag(candidateSelectedTag);
										db.updateNote(note);
									}
									// Refresh view
									((ListView) findViewById(R.id.notesList)).invalidateViews();
									// Advice to user
									String msg = getResources().getText(R.string.notes_tagged_as) + " '" + candidateSelectedTag.getName() + "'";
									Crouton.makeText(mActivity, msg, ONStyle.INFO).show();
									candidateSelectedTag = null;
									mActionMode.finish(); // Action picked, so close the CAB
								}
							}).setNeutralButton(R.string.remove_tag, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int id) {
									for (Note note : selectedNotes) {
										// Update adapter content if actual navigation is the tag
										// associated with actually cycled note										
										if ( navigation.equals(String.valueOf(note.getTag().getId())) ) {
											mAdapter.remove(note);
										}
										note.setTag(null);
										db.updateNote(note);
									}
									candidateSelectedTag = null;
									// Refresh view
									((ListView) findViewById(R.id.notesList)).invalidateViews();
									// Advice to user
									Crouton.makeText(mActivity, R.string.notes_tag_removed, ONStyle.INFO).show();
									mActionMode.finish(); // Action picked, so close the CAB
								}
							}).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int id) {
									candidateSelectedTag = null;
									mActionMode.finish(); // Action picked, so close the CAB
								}
							});

		// create alert dialog
		AlertDialog alertDialog = alertDialogBuilder.create();

		// show it
		alertDialog.show();		
	}
	
	
	
	
	private void restartAndRefresh() {
		Intent intent = getIntent();
		overridePendingTransition(0, 0);
		intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
		finish();
		overridePendingTransition(0, 0);
		startActivity(intent);
	}
	
	
	
	


}



