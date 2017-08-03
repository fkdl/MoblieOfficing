package com.r2.scau.moblieofficing.activity;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.bigkoo.quicksidebar.QuickSideBarTipsView;
import com.bigkoo.quicksidebar.QuickSideBarView;
import com.bigkoo.quicksidebar.listener.OnQuickSideBarTouchListener;
import com.r2.scau.moblieofficing.R;
import com.r2.scau.moblieofficing.adapter.SelectMemberAdapter;
import com.r2.scau.moblieofficing.bean.Contact;
import com.r2.scau.moblieofficing.gson.GsonFriend;
import com.r2.scau.moblieofficing.gson.GsonFriends;
import com.r2.scau.moblieofficing.retrofit.IFriendBiz;
import com.r2.scau.moblieofficing.untils.FistLetterUntil;
import com.r2.scau.moblieofficing.untils.OkHttpUntil;
import com.r2.scau.moblieofficing.untils.UserUntil;
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersDecoration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static com.r2.scau.moblieofficing.activity.FriendActivity.GET_FRIENDS;
import static com.r2.scau.moblieofficing.untils.UserUntil.friendList;

public class SelectMemberActivity extends BaseActivity implements OnQuickSideBarTouchListener {

    private Handler handler;
    private Toolbar mToolbar;
    private TextView mTitleTV;
    private String groupName;
    private RecyclerView mRecyclerView;
    private SelectMemberAdapter adapter;
    private QuickSideBarView mQuickSideBarView;
    private QuickSideBarTipsView mQuickSideBarTipsView;
    private List<Contact> mContactList = new ArrayList<>();
    private HashMap<String, Integer> letters = new HashMap<>();


    @Override
    protected void initView() {
        setContentView(R.layout.activity_contact);


        mRecyclerView = (RecyclerView) findViewById(R.id.rv_personal_contact);
        mQuickSideBarView = (QuickSideBarView) findViewById(R.id.qsbv_personal);
        mQuickSideBarTipsView = (QuickSideBarTipsView) findViewById(R.id.qsbtv_personal);
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mTitleTV = (TextView) findViewById(R.id.toolbar_title);

        mToolbar.setTitle("");
        mTitleTV.setText("选择群成员");

        handler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case GET_FRIENDS:
                        List<Contact> contacts = new ArrayList<Contact>();
                        contacts = (List<Contact>) msg.obj;
                        ArrayList<String> customLetters = new ArrayList<>();
                        Collections.sort(contacts);
                        int position = 0;
                        for (Contact contact : contacts) {
                            String letter = contact.getFirstLetter();
                            if (!letters.containsKey(letter)) {
                                letters.put(letter, position);
                                customLetters.add(letter);
                            }
                            position++;
                        }
                        mQuickSideBarView.setLetters(customLetters);
                        //mQuickSideBarView.invalidate();
                        adapter.addAll(contacts);
                        UserUntil.friendList = contacts;
                        break;
                }
            }
        };

        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }


    @Override
    protected void initData() {
        Intent intent = getIntent();
        groupName = intent.getStringExtra("groupName");
        ArrayList<String> customLetters = new ArrayList<>();
        if(UserUntil.friendList != null){
            mContactList = friendList;
            Collections.sort(mContactList);
            int position = 0;
            for (Contact contact : mContactList) {
                String letter = contact.getFirstLetter();
                if (!letters.containsKey(letter)) {
                    letters.put(letter, position);
                    customLetters.add(letter);
                }
                position++;
            }
        }else {
            getFriend();
        }


        initRV(customLetters);
    }

    public void initRV(ArrayList<String> customLetters) {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this,
                LinearLayoutManager.VERTICAL, false);

        adapter = new SelectMemberAdapter();
        mQuickSideBarView.setLetters(customLetters);
        adapter.addAll(mContactList);

        // Add the sticky headers decoration
        StickyRecyclerHeadersDecoration headersDecor = new StickyRecyclerHeadersDecoration(adapter);

        mRecyclerView.setAdapter(adapter);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.addItemDecoration(headersDecor);
    }


    public void getFriend() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://192.168.13.61:8089/user/")
                .callFactory(OkHttpUntil.getInstance())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        IFriendBiz iFriendBiz = retrofit.create(IFriendBiz.class);
        Call<GsonFriends> call = iFriendBiz.getFriend(UserUntil.phone);
        call.enqueue(new Callback<GsonFriends>() {
            @Override
            public void onResponse(Call<GsonFriends> call, Response<GsonFriends> response) {
                GsonFriends gsonFriends = response.body();
                if (gsonFriends.getCode() == 200) {
                    List<GsonFriend> friendList = gsonFriends.getListFriends();
                    ArrayList<Contact> contacts = new ArrayList<Contact>();
                    for (GsonFriend myFriend : friendList) {
                        Contact contact = new Contact();
                        String name = myFriend.getNickname();
                        contact.setPhone(myFriend.getUserPhone());
                        contact.setName(name);
                        contact.setFirstLetter(FistLetterUntil.getSortKey(name));
                        contacts.add(contact);
                    }
                    Message msg = new Message();
                    msg.what = GET_FRIENDS;
                    msg.obj = contacts;
                    handler.sendMessage(msg);
                    Log.e("getFriend", "success");
                } else {
                    Log.e("getFriend", gsonFriends.getMsg());
                }
            }

            @Override
            public void onFailure(Call<GsonFriends> call, Throwable t) {
                Log.e("getFriend", "fail");
            }
        });
    }


    @Override
    protected void initListener() {
        mQuickSideBarView.setOnQuickSideBarTouchListener(this);
    }

    @Override
    public void onClick(View view) {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_select_member_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.menu_select_member:
                List<Contact> selectMember = new ArrayList<>();
                for (Contact contact : mContactList){
                    if(contact.isSelect() == true){
                        selectMember.add(contact);
                    }
                }

                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onLetterChanged(String letter, int position, float y) {
        mQuickSideBarTipsView.setText(letter, position, y);
        //有此key则获取位置并滚动到该位置
        if (letters.containsKey(letter)) {
            mRecyclerView.scrollToPosition(letters.get(letter));
        }
    }

    @Override
    public void onLetterTouching(boolean touching) {
        //可以自己加入动画效果渐显渐隐
        mQuickSideBarTipsView.setVisibility(touching ? View.VISIBLE : View.INVISIBLE);
    }
}