package com.codepath.hungrybird.consumer.fragments;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.OnCompositionLoadedListener;
import com.bumptech.glide.Glide;
import com.codepath.hungrybird.R;
import com.codepath.hungrybird.common.BaseItemHolderAdapter;
import com.codepath.hungrybird.common.OrderRelationResponse;
import com.codepath.hungrybird.common.PlaceAutocompleteAdapter;
import com.codepath.hungrybird.consumer.activities.GalleryActivity;
import com.codepath.hungrybird.databinding.ConsumerCartDetailsFragmentBinding;
import com.codepath.hungrybird.databinding.ConsumerOrderCartDishItemBinding;
import com.codepath.hungrybird.model.Dish;
import com.codepath.hungrybird.model.Order;
import com.codepath.hungrybird.model.OrderDishRelation;
import com.codepath.hungrybird.model.User;
import com.codepath.hungrybird.model.postmates.DeliveryQuoteRequest;
import com.codepath.hungrybird.model.postmates.DeliveryQuoteResponse;
import com.codepath.hungrybird.network.ParseClient;
import com.codepath.hungrybird.network.PostmatesClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.AutocompletePrediction;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.parse.ParseException;
import com.parse.SaveCallback;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import jp.wasabeef.glide.transformations.RoundedCornersTransformation;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * Created by gauravb on 4/13/17.
 */

public class CartFragment extends Fragment implements GoogleApiClient.OnConnectionFailedListener {
    public static final String TAG = CartFragment.class.getSimpleName();
    public static final String OBJECT_ID = "OBJECT_ID";

    /**
     * GoogleApiClient wraps our service connection to Google Play Services and provides access
     * to the user's sign in state as well as the Google's APIs.
     */
    protected GoogleApiClient mGoogleApiClient;

    ConsumerCartDetailsFragmentBinding binding;
    ArrayList<OrderDishRelation> orderDishRelations = new ArrayList<>();

    ParseClient parseClient;

    CartFragmentListener cartFragmentListener;
    private PlaceAutocompleteAdapter mAdapter;
    private String deliveryQuoteId;
    private static DecimalFormat df = new DecimalFormat();

    static {
        df.setMinimumFractionDigits(2);
        df.setMaximumFractionDigits(2);
    }

    private static final LatLngBounds BOUNDS_SFO_BAYAREA = new LatLngBounds(
            new LatLng(36.957689, -122.657091), new LatLng(37.853184, -121.946632));


    public interface CartFragmentListener {
        public void onCheckoutListener(String orderId, String price);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.getItem(0).setVisible(false);
        super.onPrepareOptionsMenu(menu);
    }

    public static final String DISHES_PRICE = "DISHES_PRICE";
    public static final String SHIPPING_PRICE = "SHIPPING_PRICE";
    public static final String DELIVERY_ADDRESS = "DELIVERY_ADDRESS";

    private double getDishesPrice() {
        Bundle bundle = getArguments();
        return bundle.getDouble(DISHES_PRICE);
    }

    private void putDishesPrice(double price) {
        Bundle bundle = getArguments();
        bundle.putDouble(DISHES_PRICE, price);
    }

    private double getShippingCost() {
        Bundle bundle = getArguments();
        return bundle.getDouble(SHIPPING_PRICE);
    }

    private String getDisplayPrice(double price) {
        return "$" + df.format(price);
    }

    private void putShippingCost(double price) {
        Bundle bundle = getArguments();
        bundle.putDouble(SHIPPING_PRICE, price);
    }

    private void putDeliveryAddress(String deliveryAddress) {
        getArguments().putString(DELIVERY_ADDRESS, deliveryAddress);
    }

    private String getDeliveryAddress() {
        Bundle bundle = getArguments();
        return bundle.getString(DELIVERY_ADDRESS);
    }

    @Override
    public void onResume() {
        super.onResume();
        ((GalleryActivity) getActivity()).setToolbarTitle("Your Cart");
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof CartFragmentListener) {
            cartFragmentListener = (CartFragmentListener) context;
        } else {
            throw new ClassCastException(context.toString()
                    + " must implement CartFragment.CartFragmentListener");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding = DataBindingUtil.inflate(inflater, R.layout.consumer_cart_details_fragment, container, false);
        Bundle bundle = getArguments();
        String orderObjectId = bundle.getString(OBJECT_ID);
        parseClient = ParseClient.getInstance();

        binding.radioButtonPickup.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    binding.autocompletePlaces.animate().setDuration(500).scaleX(0).scaleY(0)
                            .setInterpolator(new AccelerateDecelerateInterpolator()).setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            binding.autocompletePlaces.setVisibility(View.GONE);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {

                        }
                    }).start();
                    binding.tvDeliveryCost.setVisibility(View.GONE);
                    binding.shippingServiceValue.setText(getDisplayPrice(0.00));
                    binding.consumerCartPriceBeforeTax.setText(getDisplayPrice(getDishesPrice()));
                    binding.finalTotal.setText(getDisplayPrice(getDishesPrice()));
                }
            }
        });

        binding.radioButtonDelivery.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    binding.autocompletePlaces.setVisibility(View.INVISIBLE);
                    binding.autocompletePlaces.animate().setDuration(1000).scaleX(1).scaleY(1)
                            .setInterpolator(new AccelerateDecelerateInterpolator()).setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            binding.autocompletePlaces.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {

                        }
                    }).start();


                    binding.autocompletePlaces.setSelection(0);
                    double totalPrice = getDishesPrice() + getShippingCost();
                    updateTextViewAnimation(binding.consumerCartPriceBeforeTax);
                    binding.consumerCartPriceBeforeTax.setText(getDisplayPrice(totalPrice));
                    binding.finalTotal.setText(getDisplayPrice(totalPrice));
                    binding.tvDeliveryCost.setText(getDisplayPrice(getShippingCost()));
                    binding.shippingServiceValue.setText(getDisplayPrice(getShippingCost()));

                }
            }
        });

        // Register a listener that receives callbacks when a suggestion has been selected
        binding.autocompletePlaces.setOnItemClickListener(mAutocompleteClickListener);
        // Set up the adapter that will retrieve suggestions from the Places Geo Data API that cover
        // the entire world.
        // Construct a GoogleApiClient for the {@link Places#GEO_DATA_API} using AutoManage
        // functionality, which automatically sets up the API client to handle Activity lifecycle
        // events. If your activity does not extend FragmentActivity, make sure to call connect()
        // and disconnect() explicitly.
        mGoogleApiClient = new GoogleApiClient.Builder(this.getActivity())
                .enableAutoManage(this.getActivity(), 0 /* clientId */, this)
                .addApi(Places.GEO_DATA_API)
                .build();
        AutocompleteFilter filter = new AutocompleteFilter.Builder()
                .setTypeFilter(AutocompleteFilter.TYPE_FILTER_NONE)
                .build();
        mAdapter = new PlaceAutocompleteAdapter(this.getContext(), mGoogleApiClient, BOUNDS_SFO_BAYAREA,
                filter);
        binding.autocompletePlaces.setAdapter(mAdapter);

        binding.animationView.setVisibility(View.VISIBLE);
        String assetName = "lottie_loading.json";
        LottieComposition.Factory.fromAssetFileName(this.getContext(), assetName,
                new OnCompositionLoadedListener() {
                    @Override
                    public void onCompositionLoaded(LottieComposition composition) {
                        setComposition(composition, assetName);
                    }
                });


        final BaseItemHolderAdapter<OrderDishRelation> adapter =
                new BaseItemHolderAdapter(getContext(), R.layout.consumer_order_cart_dish_item, orderDishRelations);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false);
        binding.consumerCartItemsRv.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        DividerItemDecoration simpleDividerItemDecoration =
                new DividerItemDecoration(binding.consumerCartItemsRv.getContext(), DividerItemDecoration.VERTICAL);
        binding.consumerCartItemsRv.addItemDecoration(simpleDividerItemDecoration);
        binding.consumerCartItemsRv.setAdapter(adapter);

        Observable.create(new Observable.OnSubscribe<OrderRelationResponse>() {
            @Override
            public void call(Subscriber<? super OrderRelationResponse> subscriber) {
                parseClient.getOrderById(orderObjectId, new ParseClient.OrderListener() {
                    @Override
                    public void onSuccess(Order order) {
                        OrderRelationResponse res = new OrderRelationResponse();
                        res.order = order;
                        subscriber.onNext(res);
                        subscriber.onCompleted();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        subscriber.onError(e);
                    }
                });
            }
        }).flatMap(new Func1<OrderRelationResponse, Observable<OrderRelationResponse>>() {
            @Override
            public Observable<OrderRelationResponse> call(OrderRelationResponse res) {
                return Observable.create(new Observable.OnSubscribe<OrderRelationResponse>() {
                    @Override
                    public void call(Subscriber<? super OrderRelationResponse> subscriber) {
                        parseClient.getOrderDishRelationsByOrderId(orderObjectId, new ParseClient.OrderDishRelationListListener() {
                            @Override
                            public void onSuccess(List<OrderDishRelation> orderDishRelations) {
                                res.orderDishRelation = orderDishRelations;
                                subscriber.onNext(res);
                                subscriber.onCompleted();
                            }

                            @Override
                            public void onFailure(Exception e) {
                                subscriber.onError(e);
                            }
                        });
                    }
                });
            }
        }).observeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<OrderRelationResponse>() {
                    @Override
                    public void onCompleted() {
                        binding.animationView.loop(false);
                        binding.itemsContainer.setVisibility(View.VISIBLE);
                        binding.finalCostContainer.setVisibility(View.VISIBLE);
                        binding.animationView.setVisibility(View.GONE);
                        binding.consumerCartItemsRv.setVisibility(View.VISIBLE);

                    }

                    @Override
                    public void onError(Throwable e) {
                        binding.animationView.loop(false);
                        binding.animationView.setVisibility(View.GONE);
                        binding.consumerCartItemsRv.setVisibility(View.VISIBLE);

                    }

                    @Override
                    public void onNext(OrderRelationResponse response) {
                        updatePricing(response.orderDishRelation);
                        CartFragment.this.orderDishRelations.clear();
                        CartFragment.this.orderDishRelations.addAll(response.orderDishRelation);
                        adapter.notifyDataSetChanged();

                        User chef = response.order.getChef();
                        User consumer = response.order.getConsumer();
                        Context context = getActivity();
                        if (context != null && chef.getProfileImageUrl() != null) {
                            Glide.with(context).load(chef.getProfileImageUrl()).into(binding.consumerCartChefIv);
                        }

                        binding.consumerCartChefNameTv.setText(response.order.getChef().getUsername());
                        binding.checkoutButton.setOnClickListener(v -> {
                            boolean isDelivery = binding.rgDelivery.getCheckedRadioButtonId() == R.id.radioButtonDelivery;
                            response.order.setDelivery(isDelivery ? true : false);
                            double checkOutPrice = getDishesPrice();
                            if (isDelivery && deliveryQuoteId != null) {
                                response.order.setDeliveryQuoteId(deliveryQuoteId);
                                response.order.setDeliveryFee(getShippingCost());
                                checkOutPrice += getShippingCost();
                                response.order.setDeliveryAddress(getDeliveryAddress());
                                consumer.setPrimaryAddress(getDeliveryAddress());
                                consumer.saveInBackground(new SaveCallback() {
                                    @Override
                                    public void done(ParseException e) {

                                    }
                                });
                            }
                            response.order.setTotalPayment(checkOutPrice);
                            double finalCheckoutPrice = checkOutPrice;
                            parseClient.addOrder(response.order, new ParseClient.OrderListener() {
                                @Override
                                public void onSuccess(Order order) {
                                    String price = "" + finalCheckoutPrice;
                                    Log.e("SFDSD", binding.consumerCartPriceBeforeTax.getText().toString() + " | " + price + " " + order.getTotalPayment());
                                    cartFragmentListener.onCheckoutListener(response.order.getObjectId(), price);
                                }

                                @Override
                                public void onFailure(Exception e) {

                                }
                            });
                        });

                    }
                });
        adapter.setViewBinder((holder, item, position) -> {
            holder.getBaseView().setOnClickListener(v -> {
                View addToPanel = v.findViewById(R.id.add_to_card_panel);
                togglePanel(addToPanel);
            });
            OrderDishRelation orderDishRelation = orderDishRelations.get(position);
            ConsumerOrderCartDishItemBinding binding = (ConsumerOrderCartDishItemBinding) (holder.binding);
            Dish dish = orderDishRelation.getDish();
            Context context = getContext();
            if (context != null && dish.getProfileImageUrl() != null) {
                Glide.with(context)
                        .load(dish.getProfileImageUrl())
                        .placeholder(R.drawable.placeholder)
                        .fallback(R.drawable.futurama)
                        .bitmapTransform(
                                new RoundedCornersTransformation(context, 5, 5))
                        .into(binding.itemImage);
                Glide.with(getActivity()).load(dish.getPrimaryImage().getUrl()).into(binding.itemImage);
            }
            binding.dishItemName.setText(dish.getTitle());
            binding.dishItemServiceSize.setText("Serves " + dish.getServingSize());
            binding.dishPrice.setText(getDisplayPrice(dish.getPrice()));
            binding.itemsCount.setText(" x " + orderDishRelation.getQuantity());
            binding.updatedCount.setText("" + orderDishRelation.getQuantity());
            binding.price.setText(getDisplayPrice(dish.getPrice() * orderDishRelation.getQuantity()));
            if (view != null) {
                view.setVisibility(View.VISIBLE);
            }
            if (orderDishRelation.getQuantity() == 1) {
                binding.reduceCart.setImageResource(R.drawable.ic_delete_black_24px);
            }
            binding.addToCardPanel.setVisibility(View.GONE);
            binding.itemsCount.setOnClickListener(v -> {
                int visibility = binding.addToCardPanel.getVisibility();
                togglePanel(binding.addToCardPanel);
                if (visibility == View.GONE) {
                    binding.updatedCount.setText("" + orderDishRelation.getQuantity());
                    if (orderDishRelation.getQuantity() == 1) {
                        binding.reduceCart.setImageResource(R.drawable.ic_delete_black_24px);
                    }
                } else {
                    view = null;
                }

            });
            binding.addCart.setOnClickListener(v -> {
                int val = orderDishRelation.getQuantity();
                if (val == 1) {
                    binding.reduceCart.setImageResource(R.drawable.ic_remove_circle_outline_black_24px);
                }
                orderDishRelation.setQuantity(val + 1);
                updateTextViewAnimation(binding.updatedCount);
                binding.updatedCount.setText("" + orderDishRelation.getQuantity());
                parseClient.addOrderDishRelation(orderDishRelation, new SaveCallback() {
                    @Override
                    public void done(ParseException e) {
                        if (e == null) {
                            updatePricing(orderDishRelations);
                            binding.itemsCount.setText(" x " + orderDishRelation.getQuantity());
                            updateTextViewAnimation(binding.itemsCount);
                            binding.price.setText(getDisplayPrice(dish.getPrice() * orderDishRelation.getQuantity()));
                            updateTextViewAnimation(binding.price);
                        } else {
                            binding.reduceCart.setImageResource(R.drawable.ic_delete_black_24px);
                        }
                    }
                });
            });
            binding.reduceCart.setOnClickListener(v -> {
                int val = orderDishRelation.getQuantity();
                if (val == 1) {
                    //Quantity already more than 1 so delete
                    parseClient.delete(orderDishRelation, new ParseClient.OrderDishRelationListener() {
                        @Override
                        public void onSuccess(OrderDishRelation orderDishRelation) {
                            int position = holder.getAdapterPosition();
                            orderDishRelations.remove(position);
                            adapter.notifyItemRemoved(position);
                            updatePricing(orderDishRelations);
                            binding.price.setText(getDisplayPrice(dish.getPrice() * orderDishRelation.getQuantity()));
                            updateTextViewAnimation(binding.price);
                        }

                        @Override
                        public void onFailure(Exception e) {

                        }
                    });
                } else if (val > 1) {
                    if (val == 2) {
                        binding.reduceCart.setImageResource(R.drawable.ic_delete_black_24px);
                    }
                    orderDishRelation.setQuantity(val - 1);
                    ValueAnimator fadeAnim = ObjectAnimator.ofFloat(binding.updatedCount, "alpha", 0f, 1f);
                    fadeAnim.setInterpolator(new AccelerateDecelerateInterpolator());
                    fadeAnim.setDuration(500);
                    fadeAnim.start();
                    binding.updatedCount.setText("" + orderDishRelation.getQuantity());
                    parseClient.addOrderDishRelation(orderDishRelation, new SaveCallback() {
                        @Override
                        public void done(ParseException e) {
                            if (e == null) {
                                updatePricing(orderDishRelations);
                                binding.itemsCount.setText(" x " + orderDishRelation.getQuantity());
                                binding.price.setText(getDisplayPrice(dish.getPrice() * orderDishRelation.getQuantity()));
                                updateTextViewAnimation(binding.price);
                            }
                        }
                    });
                }
            });

        });
        return binding.getRoot();
    }

    void setComposition(LottieComposition composition, String name) {
        binding.animationView.setComposition(composition);
        binding.animationView.playAnimation();
        binding.animationView.loop(true);
    }

    private void togglePanel(View addToPanel) {

        int visible = addToPanel.getVisibility();
        Log.d(TAG, "onCreateView: " + (visible == View.VISIBLE));
        if (view != null && view.getVisibility() == View.VISIBLE) {
            slideDown(view);
        }
        if (visible == View.VISIBLE) {
            slideDown(addToPanel);
        } else {
            addToPanel.setVisibility(View.VISIBLE);
            slideUp(addToPanel);
            view = addToPanel;
        }
    }

    private void slideDown(View view) {
        view.setVisibility(View.GONE);
        Animation slideUp = AnimationUtils.loadAnimation(getActivity(),
                R.anim.slide_down);
        slideUp.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                view.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        view.startAnimation(slideUp);
    }

    private void slideUp(View view) {
        view.setVisibility(View.VISIBLE);
        Animation slideUp = AnimationUtils.loadAnimation(getActivity(),
                R.anim.slide_up);
        slideUp.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                view.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        view.startAnimation(slideUp);
    }

    private void updatePricing(List<OrderDishRelation> orderDishRelations) {
        double temp = 0.0;
        int itemsCount = 0;
        for (OrderDishRelation o : orderDishRelations) {
            itemsCount += o.getQuantity();
            temp += o.getQuantity() * o.getDish().getPrice();
        }
        putDishesPrice(temp);
        double price = temp;
        if (binding.radioButtonDelivery.isChecked()) {
            price = getDishesPrice() + getShippingCost();
        }
        ValueAnimator fadeAnim = ObjectAnimator.ofFloat(binding.consumerCartPriceBeforeTax, "alpha", 0f, 1f);
        fadeAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        fadeAnim.setDuration(500);
        fadeAnim.start();
        binding.itemsTotalValue.setText("" + itemsCount);
        binding.consumerCartPriceBeforeTax.setText(getDisplayPrice(price));
        binding.finalTotal.setText(getDisplayPrice(price));
        binding.itemsTotalSubtotalValue.setText(getDisplayPrice(price));
        if (itemsCount <= 0) {
            binding.finalCostContainer.setVisibility(View.INVISIBLE);
            binding.itemsContainer.setVisibility(View.INVISIBLE);
        } else {
            binding.finalCostContainer.setVisibility(View.VISIBLE);
            binding.itemsContainer.setVisibility(View.VISIBLE);
        }
    }

    View view = null;

    /**
     * Listener that handles selections from suggestions from the AutoCompleteTextView that
     * displays Place suggestions.
     * Gets the place id of the selected item and issues a request to the Places Geo Data API
     * to retrieve more details about the place.
     *
     * @see com.google.android.gms.location.places.GeoDataApi#getPlaceById(com.google.android.gms.common.api.GoogleApiClient,
     * String...)
     */
    private AdapterView.OnItemClickListener mAutocompleteClickListener
            = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            /*
             Retrieve the place ID of the selected item from the Adapter.
             The adapter stores each Place suggestion in a AutocompletePrediction from which we
             read the place ID and title.
              */
            final AutocompletePrediction item = mAdapter.getItem(position);
            final String placeId = item.getPlaceId();
            final CharSequence primaryText = item.getPrimaryText(null);

            Log.i(TAG, "Autocomplete item selected: " + primaryText);

            /*
             Issue a request to the Places Geo Data API to retrieve a Place object with additional
             details about the place.
              */
            PendingResult<PlaceBuffer> placeResult = Places.GeoDataApi
                    .getPlaceById(mGoogleApiClient, placeId);
            placeResult.setResultCallback(mUpdatePlaceDetailsCallback);

            Log.i(TAG, "Called getPlaceById to get Place details for " + placeId);
        }
    };

    private TextView mPlaceDetailsAttribution;
    /**
     * Callback for results from a Places Geo Data API query that shows the first place result in
     * the details view on screen.
     */
    private ResultCallback<PlaceBuffer> mUpdatePlaceDetailsCallback
            = new ResultCallback<PlaceBuffer>() {
        @Override
        public void onResult(PlaceBuffer places) {
            if (!places.getStatus().isSuccess()) {
                // Request did not complete successfully
                Log.e(TAG, "Place query did not complete. Error: " + places.getStatus().toString());
                places.release();
                return;
            }
            // Get the Place object from the buffer.
            final Place place = places.get(0);
            binding.autocompletePlaces.setSelection(0);
            String dropOffAddress = binding.autocompletePlaces.getText().toString();
            if (TextUtils.isEmpty(dropOffAddress) == false) {
                putDeliveryAddress(dropOffAddress);

                binding.deliveryCostProgress.setVisibility(View.VISIBLE);
//                Toast.makeText(getContext(), "" + binding.autocompletePlaces.getText(), Toast.LENGTH_SHORT).show();
                // Get Quote
                String pickUpAddress = "800 California St #100, Mountain View, CA 94041"; // TODO integrate with real values
                DeliveryQuoteRequest deliveryQuoteRequest = new DeliveryQuoteRequest(pickUpAddress, dropOffAddress);
                PostmatesClient.getInstance().deliveryQuotes(deliveryQuoteRequest, new PostmatesClient.DeliveryQuoteResponseListener() {
                    @Override
                    public void onSuccess(DeliveryQuoteResponse deliveryQuoteResponse) {
                        deliveryQuoteId = deliveryQuoteResponse.getQuoteId();
                        putShippingCost(deliveryQuoteResponse.getFee() / 100.00);
                        binding.consumerCartPriceBeforeTax.setText(getDisplayPrice(getDishesPrice() + getShippingCost()));
                        binding.finalTotal.setText(getDisplayPrice(getDishesPrice() + getShippingCost()));
                        binding.tvDeliveryCost.setText(getDisplayPrice(getShippingCost()));
                        binding.shippingServiceValue.setText(getDisplayPrice(getShippingCost()));
                        binding.deliveryCostProgress.setVisibility(View.GONE);
                        binding.tvDeliveryCost.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        binding.tvDeliveryCost.setText("$ 1.00");
                        binding.shippingServiceValue.setText(getDisplayPrice(0.00));
                        binding.deliveryCostProgress.setVisibility(View.GONE);
                    }
                });

            }
            places.release();
        }
    };

    /**
     * Called when the Activity could not connect to Google Play services and the auto manager
     * could resolve the error automatically.
     * In this case the API is not available and notify the user.
     *
     * @param connectionResult can be inspected to determine the cause of the failure
     */
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

        Log.e(TAG, "onConnectionFailed: ConnectionResult.getErrorCode() = "
                + connectionResult.getErrorCode());

//        // TODO(Developer): Check error code and notify the user of error state and resolution.
//        Toast.makeText(getActivity(),
//                "Could not connect to Google API Client: Error " + connectionResult.getErrorCode(),
//                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
            mGoogleApiClient.stopAutoManage(getActivity());
        }
    }

    private void updateTextViewAnimation(View view) {
        ValueAnimator fadeAnim = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
        fadeAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        fadeAnim.setDuration(500);
        fadeAnim.start();
    }
}
