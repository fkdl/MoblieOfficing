package com.r2.scau.moblieofficing.fragement;


import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.r2.scau.moblieofficing.Contants;
import com.r2.scau.moblieofficing.R;
import com.r2.scau.moblieofficing.activity.AddFriendActivity;
import com.r2.scau.moblieofficing.activity.ChatActivity;
import com.r2.scau.moblieofficing.activity.EditGroupActivity;
import com.r2.scau.moblieofficing.adapter.MessageAdapter;
import com.r2.scau.moblieofficing.bean.ChatMessage;
import com.r2.scau.moblieofficing.bean.ChatRecord;
import com.r2.scau.moblieofficing.event.MessageEvent;
import com.r2.scau.moblieofficing.smack.SmackListenerManager;
import com.r2.scau.moblieofficing.smack.SmackManager;
import com.r2.scau.moblieofficing.untils.DateUtil;
import com.r2.scau.moblieofficing.untils.ToastUtils;
import com.r2.scau.moblieofficing.untils.UserUntil;
import com.xys.libzxing.zxing.activity.CaptureActivity;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.litepal.crud.DataSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import okhttp3.OkHttpClient;

import static android.app.Activity.RESULT_OK;


/**
 * Created by 嘉进 on 9:20.
 * 底部导航栏中消息的Fragment
 */

public class MessageFragment extends Fragment {
    private ArrayList<ChatRecord> msgList = new ArrayList<>();
    private MessageAdapter message_adapter;
    final private int setTopBtn = 0;
    final private int deleteBtn = 1;
    final private int deleteTopBtn = 2;
    private SmackManager smack;
    private LinearLayoutManager mLayoutManager;
    private RecyclerView recyclerView;
    private ChatRecord chatRecord;
    private ArrayList<ChatRecord> newList;
    private String flagOfMulti;
    private ArrayList<String> roomNameList;
    private OkHttpClient okHttpClient = new OkHttpClient();

    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 1;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_message, container, false);
        Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar_message_fragment);
        toolbar.inflateMenu(R.menu.toolbar_message_menu);
        toolbar.setTitle("消息");
        setHasOptionsMenu(true);

        recyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        mLayoutManager = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(mLayoutManager);

        refreshData();

        recyclerView.setItemAnimator(new DefaultItemAnimator());
        registerForContextMenu(recyclerView);

        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.scan) {
                    if(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_CONTACTS)
                            != PackageManager.PERMISSION_GRANTED){
                        ActivityCompat.requestPermissions(getActivity() ,
                                new String[]{Manifest.permission.READ_CONTACTS},
                                MY_PERMISSIONS_REQUEST_CAMERA);
                    }else {
                        openQRCodeActivity();
                    }
                }
                if (item.getItemId() == R.id.multiChat) {
                    Intent multiIntent = new Intent(getActivity().getApplicationContext(), EditGroupActivity.class);
                    startActivity(multiIntent);
                }
                if (item.getItemId() == R.id.addFriend) {
                    Intent addFriendIntent = new Intent(getActivity().getApplicationContext(), AddFriendActivity.class);
                    startActivity(addFriendIntent);
                }
                if (item.getItemId() == R.id.cloud) {
                }
                return true;
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);
        Log.e("creat", "creat");
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroyView() {

        super.onDestroyView();
        EventBus.getDefault().unregister(this);
        message_adapter = null;
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onChatRecordEvent(ChatRecord event) {
        //向其他人发起聊天时接收到的事件
        if (isRemoving() || message_adapter == null) {
            return;
        }
        if (message_adapter.getMessageList().indexOf(event) > -1) {
            return;//已经存在此人的聊天窗口记录
        }
        addChatRecord(event);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onReceiveChatMessageEvent(MessageEvent message) {
        //收到发送的消息时接收到的事件(包括别人发送的和自己发送的消息)
        Log.d("chatRecord", "chatRecord");
        if (isRemoving() || message_adapter == null) {
            return;
        }
        ChatRecord chatRecord = getChatRecord(message.getChatMessage());
//        chatRecord.setSetTopFlag(false);
        if (chatRecord == null) {//还没有创建此朋友的聊天记录
            Log.d("chatRecord1", "chatRecord1");
            message.getChatMessage().save();
            chatRecord = new ChatRecord(message.getChatMessage());
            chatRecord.setSetTopFlag(false);
            addChatRecord(chatRecord);
        } else {
            Log.d("chatRecord2", "chatRecord2");
            if (!message.getChatMessage().isMeSend()) {
                ArrayList<ChatMessage> multiMsg = new ArrayList<>(DataSupport.where("msgid=?", message.getChatMessage().getMsgID()).find(ChatMessage.class));
                if (multiMsg.size() != 0) {
                    refreshData();
                    return;
                } else {
                    message.getChatMessage().save();
                }
            }
            chatRecord.setmChatTime(message.getChatMessage().getDatetime());
            chatRecord.setmLastMessage(message.getChatMessage().getContent());
            chatRecord.setSetTopFlag(false);
            if (message.getChatMessage().isMeSend()) {
//                chatRecord.updateUnReadMessageCount();
            } else {
                chatRecord.updateUnReadMessageCount();
            }
//            message_adapter.update(chatRecord);
            if (!message.getChatMessage().isMulti()) {
                chatRecord.updateAll("mchatjid=? and mfriendusername=?", chatRecord.getmChatJid(), chatRecord.getmFriendUsername());
            } else {
                chatRecord.updateAll("mchatjid=? and mfriendusername=?", chatRecord.getmChatJid(), chatRecord.getmFriendUsername());
            }
            refreshData();
        }
    }


    /**
     * 根据消息获取聊天记录窗口对象
     *
     * @param message
     * @return
     */
    private ChatRecord getChatRecord(ChatMessage message) {

        ChatRecord chatRecord = null;
        List<ChatRecord> list = DataSupport.findAll(ChatRecord.class);
        for (int i = 0; i < list.size(); i++) {
            chatRecord = list.get(i);
            if (chatRecord.getmFriendUsername().equals(message.getFriendUsername())) {
                return chatRecord;
            } else {
                chatRecord = null;
            }

        }

        return chatRecord;
    }

    private void addChatRecord(ChatRecord chatRecord) {
        chatRecord.save();
        refreshData();
        mLayoutManager.scrollToPosition(0);
    }


    //创建contextmenu
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (msgList.get(message_adapter.getmPosition()).getSetTopFlag()) {
            menu.add(Menu.NONE, deleteTopBtn, Menu.NONE, "取消置顶");//groupId, itemId, order, title
            menu.add(Menu.NONE, deleteBtn, Menu.NONE, "删除");
        } else {
            menu.add(Menu.NONE, setTopBtn, Menu.NONE, "置顶");//groupId, itemId, order, title
            menu.add(Menu.NONE, deleteBtn, Menu.NONE, "删除");
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case setTopBtn:
                chatRecord = message_adapter.getMessageList().get(message_adapter.getmPosition());
                if (chatRecord.isSaved()) {
                    chatRecord.setSetTopFlag(true);
                    chatRecord.save();
                    refreshData();
                }
                break;
            case deleteBtn:
//                message_adapter.deleteMsg();
                chatRecord = message_adapter.getMessageList().get(message_adapter.getmPosition());
                if (chatRecord.isSaved()) {
//                    chatRecord.setSetTopFlag(true);
//                    chatRecord.save();
//                    refreshData();
                    if (chatRecord.ismIsMulti()) {
                        flagOfMulti = "1";
                    } else {
                        flagOfMulti = "0";
                    }
//                    DataSupport.deleteAll(ChatMessage.class, "mfriendusername=? and mismulti=?", chatRecord.getmFriendUsername(), flagOfMulti);
                    chatRecord.delete();
                    refreshData();
                }
                break;
            case deleteTopBtn:
                chatRecord = message_adapter.getMessageList().get(message_adapter.getmPosition());
                if (chatRecord.isSaved()) {
                    chatRecord.setSetTopFlag(false);
                    chatRecord.save();
                    refreshData();
                }
        }
        return true;
    }


    public void openQRCodeActivity(){
        Intent intent = new Intent(getActivity(),CaptureActivity.class);
        getActivity().startActivityForResult(intent, Contants.RequestCode.QRSCAN);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.toolbar_message_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.scan) {
        }


        return true;
    }


    public void refreshData() {
        //我的用户名
        String whereClause = UserUntil.gsonUser.getUserPhone();
//        String whereClause = "sure1";
        msgList = new ArrayList<>(DataSupport.where("mmeusername=?", whereClause)
                .where("settopflag=?", "1")
                .order("mchattime desc")
                .find(ChatRecord.class));
        newList = new ArrayList<>(DataSupport.where("mmeusername=?", whereClause)
                .where("settopflag=?", "0")
                .order("mchattime desc")
                .find(ChatRecord.class));
        msgList.addAll(newList);
        message_adapter = new MessageAdapter(getContext(), msgList);
        recyclerView.setAdapter(message_adapter);
    }


    public void startMultiChat(Context context, MultiUserChat multiUserChat) {
        ChatRecord record;
        List<ChatRecord> chatRecords = DataSupport.where("mfriendusername=?", multiUserChat.getRoom()).find(ChatRecord.class);
        if (chatRecords.size() == 0) {
            record = new ChatRecord();
            String friendUserName = multiUserChat.getRoom();
            int idx = friendUserName.indexOf("@conference.");
            String friendNickName = friendUserName.substring(0, idx);
            record.setUuid(UUID.randomUUID().toString());
            record.setmFriendUsername(friendUserName);
            record.setmFriendNickname(friendNickName);
            record.setmMeUsername("sure3");
            record.setmMeNickname("张大爷");
            record.setmChatTime(DateUtil.currentDatetime());
            record.setmIsMulti(true);
            record.save();
        } else {
            record = chatRecords.get(0);
        }
        EventBus.getDefault().post(record);
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra("chatrecord", record);
        startActivity(intent);
    }

    //单人聊天离线消息接受
    public void getUnReanMsg() {

    }


    //检查离线期间加入的群聊
    public void checkMultiInvite() {
        String roomName = "dasdsadsadsa@conference.192.168.13.57";
        ChatRecord record;
        List<ChatRecord> chatRecords = DataSupport.where("mfriendusername=?", roomName).find(ChatRecord.class);
        if (chatRecords.size() == 0) {
            record = new ChatRecord();
            String friendUserName = roomName;
            int idx = friendUserName.indexOf("@conference.");
            String friendNickName = friendUserName.substring(0, idx);
            record.setUuid(UUID.randomUUID().toString());
            record.setmFriendUsername(friendUserName);
            record.setmFriendNickname(friendNickName);
            record.setmMeUsername("sure3");
            record.setmMeNickname("sure3");
            record.setmChatTime(DateUtil.currentDatetime());
            record.setmIsMulti(true);
            record.save();
            MultiUserChat multiChatRoom = SmackManager.getInstance().getMultiChat(roomName);
            SmackListenerManager.addMultiChatMessageListener(multiChatRoom);
            SmackManager.getInstance().joinChatRoom("dasdsadsadsa@conference.192.168.13.57", "sure3", null);
        } else {
            record = chatRecords.get(0);
        }
        EventBus.getDefault().post(record);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode){
            case Contants.RequestCode.QRSCAN:
                if (resultCode == RESULT_OK){
                    /**
                     * Create by edwincheng in 2017/08/04
                     * resultdata代表的是 二维码内部储存的信息
                     *
                     * 自己解析resultdata中的字段 然后在做相应操作
                     */
                    String resultdata = data.getStringExtra("result");
                    ToastUtils.show(getActivity(),resultdata, Toast.LENGTH_SHORT);
                    Log.e("二维码扫描结果", resultdata);
                }else {
                    Log.e("二维码扫描结果", "用户选择取消" );
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == MY_PERMISSIONS_REQUEST_CAMERA){
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                Log.e("permission", "accept");
                openQRCodeActivity();
            } else
            {
                // Permission Denied
                Toast.makeText(getActivity(), "Permission Denied", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


}
