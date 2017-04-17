package com.codepath.hungrybird.consumer.fragments;


import android.content.Context;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.codepath.hungrybird.R;
import com.codepath.hungrybird.common.BaseItemHolderAdapter;
import com.codepath.hungrybird.common.DateUtils;
import com.codepath.hungrybird.common.StringsUtils;
import com.codepath.hungrybird.databinding.ConsumerOrderHistoryViewItemBinding;
import com.codepath.hungrybird.databinding.OrderHistoryBinding;
import com.codepath.hungrybird.model.Order;
import com.codepath.hungrybird.network.ParseClient;
import com.parse.ParseObject;
import com.parse.ParseUser;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class OrderHistoryFramgent extends Fragment {
    OrderHistoryBinding binding;
    private ArrayList<Order> orders = new ArrayList<>();
    private DateUtils dateUtils = new DateUtils();
    private OnOrderSelected onOrderSelected;

    public interface OnOrderSelected {
        void onOrderSelected(String orderId);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        onOrderSelected = (OnOrderSelected) context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = DataBindingUtil.inflate(inflater, R.layout.order_history, container, false);

        final BaseItemHolderAdapter<Order> adapter =
                new BaseItemHolderAdapter<>(getContext(), R.layout.consumer_order_history_view_item, orders);
        adapter.setOnClickListener(v -> {
            if (onOrderSelected != null) {
                Object object = v.getTag();
                ParseObject parseObject = (ParseObject) object;
                onOrderSelected.onOrderSelected(parseObject.getObjectId());
            }
        });
        binding.consumerOrderListRv.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        binding.consumerOrderListRv.setAdapter(adapter);
        ParseClient.getInstance().getOrdersByConsumerId(ParseUser.getCurrentUser().getObjectId(), new ParseClient.OrderListListener() {
            @Override
            public void onSuccess(List<Order> os) {
                Observable.from(os)
                        .filter(order -> {
                            boolean ret = true;
                            try {
                                String shortDate = dateUtils.getDate(order.getUpdatedAt());
                                order.setShortDate(shortDate);
                                if (Order.Status.NOT_ORDERED.name().equals(order.getStatus())) {
                                    order.setStatus("Not Ordered");
                                    ret = false;
                                } else {
                                    StringsUtils stringsUtils = new StringsUtils();
                                    String status = stringsUtils.displayStatusString(order);
                                    if (status != null) {
                                        order.setStatus(status);
                                    }
                                }
                                ret = true;
                            } catch (Exception e) {
                                e.printStackTrace();
                                ret = false;
                            }
                            return ret;
                        })
                        .toList()
                        .subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread()).subscribe(orders1 -> {
                    orders.clear();
                    orders.addAll(orders1);
                    adapter.notifyDataSetChanged();

                });
            }

            @Override
            public void onFailure(Exception e) {

            }
        });
        adapter.setViewBinder((holder, item, position) -> {
            Order order = orders.get(position);
            ConsumerOrderHistoryViewItemBinding binding = (ConsumerOrderHistoryViewItemBinding) (holder.binding);
            binding.consumerOrderDateTv.setText(order.getShortDate());
            binding.consumerOrderStatus.setText(order.getStatus());
            binding.consumerOrderCode.setText(order.getObjectId());
            binding.consumerOrderDeliveryStatus.setText(order.getStatus());
            Object o = order.get("chef");
            ParseObject po = (ParseObject) o;
            String chefName = (String) (po.get("chefName"));
            binding.consumerOrderChefName.setText(chefName);

        });
        return binding.getRoot();

    }
}
