package com.mogujie.tt.ui.fragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.mogujie.tt.R;
import com.mogujie.tt.adapter.EntityListViewAdapter;
import com.mogujie.tt.adapter.EntityListViewAdapter.ViewHolder;
import com.mogujie.tt.adapter.GroupManagerAdapter;
import com.mogujie.tt.adapter.GroupManagerAdapter.OnDeleteItemListener;
import com.mogujie.tt.config.SysConstant;
import com.mogujie.tt.imlib.IMActions;
import com.mogujie.tt.imlib.IMContactManager;
import com.mogujie.tt.imlib.IMGroupManager;
import com.mogujie.tt.imlib.IMSession;
import com.mogujie.tt.imlib.proto.ContactEntity;
import com.mogujie.tt.imlib.service.IMService;
import com.mogujie.tt.imlib.utils.IMUIHelper;
import com.mogujie.tt.imlib.utils.IMUIHelper.SessionInfo;
import com.mogujie.tt.ui.base.TTBaseFragment;
import com.mogujie.tt.ui.utils.EntityList;
import com.mogujie.tt.ui.utils.IMGroupMemberGridViewHelper;
import com.mogujie.tt.ui.utils.IMServiceHelper;
import com.mogujie.tt.ui.utils.IMServiceHelper.OnIMServiceListner;
import com.mogujie.tt.utils.ContactUtils;
import com.mogujie.tt.widget.SearchEditText;
import com.mogujie.tt.widget.SortSideBar;
import com.mogujie.tt.widget.SortSideBar.OnTouchingLetterChangedListener;

public class GroupMemberSelectFragment extends TTBaseFragment
		implements
			OnIMServiceListner,
			OnTouchingLetterChangedListener,
			OnItemClickListener {
	@Override
	public void onDestroyView() {
		imServiceHelper.disconnect(getActivity());
		// TODO Auto-generated method stub
		super.onDestroyView();
	}

	private View curView = null;
	private ListView contactListView;
	private IMServiceHelper imServiceHelper = new IMServiceHelper();
	private IMService imService;
	private IMGroupMemberGridViewHelper gridViewHelper = new IMGroupMemberGridViewHelper();
	private EntityListViewAdapter contactAdapter;
	private SortSideBar sortSideBar;
	private TextView dialog;
	private SearchEditText searchEditText;
	private IMUIHelper.SessionInfo sessionInfo;
	private EntityList entityList;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		if (null != curView) {
			((ViewGroup) curView.getParent()).removeView(curView);
			return curView;
		}
		curView = inflater.inflate(R.layout.tt_fragment_group_member_select, topContentView);

		List<String> actions = new ArrayList<String>();
		actions.add(IMActions.ACTION_GROUP_CREATE_TEMP_GROUP_RESULT);
		actions.add(IMActions.ACTION_GROUP_CHANGE_TEMP_GROUP_MEMBER_RESULT);

		imServiceHelper.connect(getActivity(), actions, IMServiceHelper.INTENT_NO_PRIORITY, this);

		initRes();

		return curView;
	}

	/**
	 * @Description 初始化资源
	 */
	private void initRes() {
		// 设置标题栏
		// todo eric
		setTopTitle("编辑群联系人");
		setTopLeftButton(R.drawable.tt_top_back);
		setTopLeftText(getActivity().getString(R.string.top_left_back));
		setTopRightText(getActivity().getString(R.string.save));

		topLeftBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				getActivity().finish();
			}
		});

		topRightTitleTxt.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				logger.d("tempgroup#on 'save' btn clicked");

				IMGroupManager groupMgr = imService.getGroupManager();
				GroupManagerAdapter adapter = gridViewHelper.getAdapter();

				int sessionType = GroupMemberSelectFragment.this.sessionInfo.getSessionType();
				if (sessionType == IMSession.SESSION_P2P) {
					List<String> memberList = adapter.getMemberList();

					String loginId = imService.getLoginManager().getLoginId();
					logger.d("tempgroup#loginId:%s", loginId);
					memberList.add(0, loginId);

					logger.d("tempgroup#memberList size:%d", memberList.size());
					for (String id : memberList) {
						logger.d("tempgroup#member:%s", id);
					}

					String tempGroupName = generateTempGroupName(memberList);
					logger.d("tempgroup#generateTempGroupName:%s", tempGroupName);

					groupMgr.reqCreateTempGroup(tempGroupName, memberList);

				} else if (sessionType == IMSession.SESSION_TEMP_GROUP) {
					List<String> addingMemberList = gridViewHelper.getAdapter().getAddingMemberList();
					List<String> removingMemberList = gridViewHelper.getAdapter().getRemovingMemberList();

					imService.getGroupManager().changeTempGroupMembers(sessionInfo.getSessionId(), addingMemberList, removingMemberList);
				}

			}

			private String generateTempGroupName(List<String> memberList) {
				int MAX_NAME_PREFIX_LEN = 15;

				String name = "";
				IMContactManager contactMgr = imService.getContactManager();

				ContactEntity prevContact = null;
				for (String id : memberList) {
					ContactEntity contact = contactMgr.findContact(id);
					if (contact == null) {
						continue;
					}

					logger.d("tempgroup#member contact:%s", contact);

					if (prevContact != null) {
						name += "," + contact.name;
					} else {
						// first element
						name += contact.name;
					}

					if (name.length() >= MAX_NAME_PREFIX_LEN) {
						name = name.substring(0, MAX_NAME_PREFIX_LEN);
						name += "...";
						break;
					}

					prevContact = contact;
				}

				return name;
			}
		});

		sortSideBar = (SortSideBar) curView.findViewById(R.id.sidrbar);
		sortSideBar.setOnTouchingLetterChangedListener(this);

		dialog = (TextView) curView.findViewById(R.id.dialog);
		sortSideBar.setTextView(dialog);

		contactListView = (ListView) curView.findViewById(R.id.all_contact_list);
		contactListView.setOnItemClickListener(this);

		contactAdapter = new EntityListViewAdapter(getActivity());
		contactAdapter.showCheckbox();
		contactListView.setAdapter(contactAdapter);

		searchEditText = (SearchEditText) curView.findViewById(R.id.filter_edit);

		searchEditText.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				contactAdapter.onSearch(s.toString());
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {

			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		gridViewHelper.onInit(curView, R.id.group_manager_grid, getActivity(), false, null, new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					int position, long id) {

				gridViewHelper.getAdapter().setRemoveState(true);
				gridViewHelper.getAdapter().notifyDataSetChanged();

				return false;
			}
		}, new OnDeleteItemListener() {

			@Override
			public void onDeleteItem(String contactId) {
				((BaseAdapter) contactListView.getAdapter()).notifyDataSetChanged();
			}
		});
	}

	private void initContactList(Set<String> removedIdSet) {
		Map<String, ContactEntity> contacts = imService.getContactManager().getContacts();
		List<Object> contactList = IMUIHelper.getContactSortedList(contacts);
		List<Object> finalContactList = new ArrayList<Object>();
		for (Object obj : contactList) {
			ContactEntity contact = (ContactEntity) obj;
			if (contact == null) {
				continue;
			}

			if (removedIdSet.contains(contact.id)) {
				continue;
			}

			finalContactList.add(obj);
		}

		entityList = new EntityList(finalContactList) {

			@Override
			public boolean shouldCheckBoxChecked(int position) {
				ContactEntity contact = (ContactEntity) list.get(position);
				if (contact == null) {
					return false;
				}

				return gridViewHelper.getAdapter().isInGroup(contact.id);
			}

			@Override
			public void onItemClick(View view, int position) {
				// TODO Auto-generated method stub
				handleContactItemClick(view, GroupMemberSelectFragment.this.getActivity(), position);
			}

			private void handleContactItemClick(View view, Context ctx,
					int position) {
				logger.d("contactUI#handleContactItemClick position:%d", position);

				ContactEntity contact = (ContactEntity) list.get(position);
				logger.d("chat#clicked contact:%s", contact);

				ViewHolder viewHolder = (ViewHolder) view.getTag();
				if (viewHolder == null) {
					return;
				}

				viewHolder.checkBox.toggle();

				boolean checked = viewHolder.checkBox.isChecked();
				if (checked) {
					gridViewHelper.getAdapter().add(contact);
				} else {
					gridViewHelper.getAdapter().removeById(contact.id);
				}
			}

			@Override
			public String getSectionName(int position) {
				if (searchMode) {
					return "";
				}

				// TODO Auto-generated method stub
				final ContactEntity contact = (ContactEntity) list.get(position);
				if (contact == null) {
					return "";
				}

				String sectionName = ContactUtils.getSectionName(contact);
				if (position == 0) {
					return sectionName;
				}

				ContactEntity upperContact = (ContactEntity) list.get(position - 1);
				if (sectionName.equals(ContactUtils.getSectionName(upperContact))) {
					return "";
				} else {
					return sectionName;
				}

			}

			public boolean isPinYinIndexable() {
				return true;
			}

			public int getPinYinFirstCharacter(int position) {
				// TODO Auto-generated method stub
				final ContactEntity contact = (ContactEntity) list.get(position);
				if (contact == null) {
					return 0;
				}

				return contact.pinyin.charAt(0);
			}

			@Override
			public void onSearchImpl(String key) {
				ArrayList<Object> searchList = new ArrayList<Object>();
				for (Object obj : list) {
					ContactEntity contact = (ContactEntity) obj;
					if (contact.pinyin.contains(key)
							|| contact.name.contains(key)) {
						searchList.add(obj);
					}
				}

				list = searchList;
			}
		};

		contactAdapter.add(0, entityList);
	}

	@Override
	public void onAction(String action, Intent intent,
			BroadcastReceiver broadcastReceiver) {
		// TODO Auto-generated method stub

		if (action.equals(IMActions.ACTION_GROUP_CREATE_TEMP_GROUP_RESULT)) {
			handleAddTempGroupResult(intent);
		} else if (action.equals(IMActions.ACTION_GROUP_CHANGE_TEMP_GROUP_MEMBER_RESULT)) {
			handleChangeTempGroupMemberResult(intent);
		}
	}

	private void handleChangeTempGroupMemberResult(Intent intent) {
		logger.d("tempgroup#handleChangeTempGroupMemberResult");
		boolean ok = intent.getBooleanExtra(SysConstant.OPERATION_RESULT_KEY, false);
		if (!ok) {
			Toast.makeText(getActivity(), getString(R.string.change_temp_group_member_failed), Toast.LENGTH_SHORT).show();
			return;
		}

		String sessionId = intent.getStringExtra(SysConstant.SESSION_ID_KEY);
		logger.d("tempgroup#result ok, sessionId:%s", sessionId);

		//todo eric adding an notification to the chat window, tell that "members are changed, right now, members are 'a', 'b', 'c' "
		IMUIHelper.openSessionChatActivity(logger, getActivity(), sessionId, IMSession.SESSION_TEMP_GROUP, imService);

		getActivity().finish();
	}

	private void handleAddTempGroupResult(Intent intent) {
		logger.d("groupmgr#on ACTION_GROUP_CREATE_TEMP_GROUP_RESULT");
		int result = intent.getIntExtra(SysConstant.OPERATION_RESULT_KEY, -1);
		if (result != 0) {
			logger.d("groupmgr#result failed");

			Toast.makeText(getActivity(), getString(R.string.create_temp_group_failed), Toast.LENGTH_SHORT).show();
			return;
		}

		String sessionId = intent.getStringExtra(SysConstant.SESSION_ID_KEY);

		logger.d("groupmgr#result ok");

		IMUIHelper.openSessionChatActivity(logger, getActivity(), sessionId, IMSession.SESSION_TEMP_GROUP, imService);

		getActivity().finish();
	}

	@Override
	public void onIMServiceConnected() {
		// TODO Auto-generated method stub
		logger.d("groupmgr#onIMServiceConnected");

		imService = imServiceHelper.getIMService();

		Intent intent = getActivity().getIntent();
		sessionInfo = IMUIHelper.getSessionInfoFromIntent(intent);

		Set<String> removedIdSet = new HashSet<String>();
		removedIdSet.add(imService.getLoginManager().getLoginId());

		int sessionType = sessionInfo.getSessionType();
		if (sessionType == IMSession.SESSION_P2P) {
			removedIdSet.add(sessionInfo.getSessionId());
		}

		gridViewHelper.getAdapter().setFixIdSet(removedIdSet);
		gridViewHelper.onSetGridData(imService, intent);

		initContactList(removedIdSet);
	}

	@Override
	protected void initHandler() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onTouchingLetterChanged(String s) {
		// TODO Auto-generated method stub
		int position = contactAdapter.getPositionForSection(s.charAt(0));
		if (position != -1) {
			contactListView.setSelection(position);
		}

	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View view, int position,
			long arg3) {
		logger.d("groupmgr#onItemClick position:%d", position);

		// TODO Auto-generated method stub
		contactAdapter.handleItemClick(view, getActivity(), position);
	}
}
