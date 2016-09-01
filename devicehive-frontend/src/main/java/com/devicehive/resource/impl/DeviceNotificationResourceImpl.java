package com.devicehive.resource.impl;

import com.devicehive.application.DeviceHiveApplication;
import com.devicehive.auth.HivePrincipal;
import com.devicehive.configuration.Messages;
import com.devicehive.json.strategies.JsonPolicyDef;
import com.devicehive.model.DeviceNotification;
import com.devicehive.model.ErrorResponse;
import com.devicehive.model.wrappers.DeviceNotificationWrapper;
import com.devicehive.resource.DeviceNotificationResource;
import com.devicehive.resource.converters.TimestampQueryParamParser;
import com.devicehive.resource.util.CommandResponseFilterAndSort;
import com.devicehive.resource.util.ResponseFactory;
import com.devicehive.service.DeviceNotificationService;
import com.devicehive.service.DeviceService;
import com.devicehive.shim.api.client.RpcClient;
import com.devicehive.vo.DeviceVO;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.devicehive.json.strategies.JsonPolicyDef.Policy.NOTIFICATION_TO_DEVICE;
import static javax.ws.rs.core.Response.Status.*;

/**
 * {@inheritDoc}
 */
@Service
public class DeviceNotificationResourceImpl implements DeviceNotificationResource {
    private static final Logger logger = LoggerFactory.getLogger(DeviceNotificationResourceImpl.class);

    @Autowired
    private DeviceNotificationService notificationService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    @Qualifier(DeviceHiveApplication.MESSAGE_EXECUTOR)
    private ExecutorService mes;

    @Autowired
    private RpcClient rpcClient;

    /**
     * {@inheritDoc}
     */
    @Override
    public void query(String guid, String startTs, String endTs, String notification, String sortField,
                      String sortOrderSt, Integer take, Integer skip, Integer gridInterval,
                      @Suspended final AsyncResponse asyncResponse) {
        logger.debug("Device notification query requested for device {}", guid);
        Date timestamp = TimestampQueryParamParser.parse(startTs);

        HivePrincipal principal = (HivePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        DeviceVO byGuidWithPermissionsCheck = deviceService.findByGuidWithPermissionsCheck(guid, principal);
        if (byGuidWithPermissionsCheck == null) {
            ErrorResponse errorCode = new ErrorResponse(NOT_FOUND.getStatusCode(), String.format(Messages.DEVICE_NOT_FOUND, guid));
            Response response = ResponseFactory.response(NOT_FOUND, errorCode);
            asyncResponse.resume(response);
        } else {
            List<String> notificationNames = StringUtils.isNoneEmpty(notification) ? Collections.singletonList(notification) : null;
            notificationService.find(Collections.singletonList(guid), notificationNames, timestamp, take)
                    .thenApply(notifications -> {
                        final Comparator<DeviceNotification> comparator = CommandResponseFilterAndSort.buildDeviceNotificationComparator(sortField);
                        final Boolean reverse = sortOrderSt == null ? null : "desc".equalsIgnoreCase(sortOrderSt);

                        final List<DeviceNotification> sortedDeviceNotifications = CommandResponseFilterAndSort.orderAndLimit(notifications, comparator, reverse, skip, take);
                        return ResponseFactory.response(Response.Status.OK, sortedDeviceNotifications, JsonPolicyDef.Policy.NOTIFICATION_TO_CLIENT);
                    })
                    .exceptionally(e -> {
                        //TODO [rafa] change error message here
                        logger.warn("Device notification get failed. NOT FOUND: No notification with id = {} found for device with guid = {}", -1l, guid);
                        ErrorResponse errorCode = new ErrorResponse(NOT_FOUND.getStatusCode(), String.format(Messages.NOTIFICATION_NOT_FOUND, -1l));
                        return ResponseFactory.response(NOT_FOUND, errorCode);
                    })
                    .thenAccept(asyncResponse::resume);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void get(String guid, Long notificationId, @Suspended final AsyncResponse asyncResponse) {
        logger.debug("Device notification requested. Guid {}, notification id {}", guid, notificationId);

        final HivePrincipal principal = (HivePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        DeviceVO device = deviceService.findByGuidWithPermissionsCheck(guid, principal);

        if (device == null) {
            ErrorResponse errorCode = new ErrorResponse(NOT_FOUND.getStatusCode(), String.format(Messages.DEVICE_NOT_FOUND, guid));
            Response response = ResponseFactory.response(NOT_FOUND, errorCode);
            asyncResponse.resume(response);
        } else {
            notificationService.find(notificationId, guid)
                    .thenApply(notification -> notification
                            .map(n -> {
                                logger.debug("Device notification proceed successfully");
                                return ResponseFactory.response(Response.Status.OK, n, JsonPolicyDef.Policy.NOTIFICATION_TO_CLIENT);
                            }).orElseGet(() -> {
                                logger.warn("Device notification get failed. NOT FOUND: No notification with id = {} found for device with guid = {}", notificationId, guid);
                                ErrorResponse errorCode = new ErrorResponse(NOT_FOUND.getStatusCode(), String.format(Messages.NOTIFICATION_NOT_FOUND, notificationId));
                                return ResponseFactory.response(NOT_FOUND, errorCode);
                            }))
                    .exceptionally(e -> {
                        //TODO: change error message here
                        logger.warn("Device notification get failed. NOT FOUND: No notification with id = {} found for device with guid = {}", notificationId, guid);
                        ErrorResponse errorCode = new ErrorResponse(NOT_FOUND.getStatusCode(), String.format(Messages.NOTIFICATION_NOT_FOUND, notificationId));
                        return ResponseFactory.response(NOT_FOUND, errorCode);
                    })
                    .thenAccept(asyncResponse::resume);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void poll(final String deviceGuid, final String namesString, final String timestamp, long timeout, final AsyncResponse asyncResponse) throws Exception {
        poll(timeout, deviceGuid, namesString, timestamp, asyncResponse);
    }

    @Override
    public void pollMany(long timeout, String deviceGuidsString, final String namesString, final String timestamp, final AsyncResponse asyncResponse) throws Exception {
        poll(timeout, deviceGuidsString, namesString, timestamp, asyncResponse);
    }

    private void poll(final long timeout,
                      final String deviceGuidsString,
                      final String namesString,
                      final String timestamp,
                      final AsyncResponse asyncResponse) throws InterruptedException {
        final HivePrincipal principal = (HivePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        final Date ts = TimestampQueryParamParser.parse(timestamp);

        if (timeout < 0) {
            submitEmptyResponse(asyncResponse);
        }
        asyncResponse.setTimeout(timeout, TimeUnit.SECONDS);

        Set<String> availableDevices = Optional.ofNullable(StringUtils.split(deviceGuidsString, ','))
                .map(Arrays::asList)
                .map(list -> deviceService.findByGuidWithPermissionsCheck(list, principal))
                .map(list -> list.stream().map(DeviceVO::getGuid).collect(Collectors.toSet()))
                .orElse(Collections.emptySet());

        Set<String> notifications = Optional.of(StringUtils.split(namesString, ','))
                .map(Arrays::asList)
                .map(list -> list.stream().collect(Collectors.toSet()))
                .orElse(Collections.emptySet());

        String subscriptionId = UUID.randomUUID().toString();
        Consumer<DeviceNotification> callback = notification -> {
            if (!asyncResponse.isDone()) {
                asyncResponse.resume(
                        ResponseFactory.response(Response.Status.OK, notification, JsonPolicyDef.Policy.NOTIFICATION_TO_CLIENT));
            }
        };
        CompletableFuture<Collection<DeviceNotification>> future =
                notificationService.sendSubscribeRequest(subscriptionId, availableDevices, notifications, ts, callback);
        future.thenAccept(collection -> {
            if (!collection.isEmpty() && !asyncResponse.isDone()) {
                asyncResponse.resume(
                        ResponseFactory.response(Response.Status.OK, collection, JsonPolicyDef.Policy.NOTIFICATION_TO_CLIENT));
            }
        }).exceptionally(throwable -> {
            if (!asyncResponse.isDone()) {
                asyncResponse.resume(throwable);
            }
            return null;
        });

        asyncResponse.register(new CompletionCallback() {
            @Override
            public void onComplete(Throwable throwable) {
                //todo unsubscribe
            }
        });
    }

//    private void getOrWaitForNotifications(final HivePrincipal principal, final String devices,
//                                           final String names, final Date timestamp, long timeout,
//                                           final AsyncResponse asyncResponse, final boolean isMany) {
//        logger.debug("Device notification pollMany requested for : {}, {}, {}.  Timeout = {}", devices, names, timestamp, timeout);
//
//        if (timeout < 0) {
//            submitEmptyResponse(asyncResponse);
//        }
//
//        final Set<String> availableGuids = (StringUtils.isBlank(devices))
//                ? Collections.emptySet()
//                : deviceService.findByGuidWithPermissionsCheck(ParseUtil.getList(devices), principal).stream()
//                .map(DeviceVO::getGuid)
//                .collect(Collectors.toSet());
//
//        final List<String> notificationNames = ParseUtil.getList(names);
//        Collection<DeviceNotification> list = new ArrayList<>();
//        final UUID reqId = UUID.randomUUID();
//        NotificationSubscriptionStorage storage = subscriptionManager.getNotificationSubscriptionStorage();
//        Set<NotificationSubscription> subscriptionSet = new HashSet<>();
//        FutureTask<Void> simpleWaitTask = new FutureTask<>(Runnables.doNothing(), null);
//
//        if (!availableGuids.isEmpty()) {
//            subscriptionSet.addAll(availableGuids.stream().map(guid ->
//                    getNotificationInsertSubscription(principal, guid, reqId, names, asyncResponse, isMany, simpleWaitTask))
//                    .collect(Collectors.toList()));
//        } else {
//            subscriptionSet.add(getNotificationInsertSubscription(principal, Constants.NULL_SUBSTITUTE, reqId, names,
//                    asyncResponse, isMany, simpleWaitTask));
//        }
//
//        if (timestamp != null && !availableGuids.isEmpty()) {
//            list = notificationService.find(availableGuids, notificationNames, timestamp, DEFAULT_TAKE).join();
//        }
//
//        if (!list.isEmpty()) {
//            Response response = ResponseFactory.response(Response.Status.OK, list, JsonPolicyDef.Policy.NOTIFICATION_TO_CLIENT);
//            logger.debug("Notifications poll result: {}", response.getEntity());
//            asyncResponse.resume(response);
//        } else {
//            if (!SimpleWaiter.subscribeAndWait(storage, subscriptionSet, new FutureTask<>(Runnables.doNothing(), null), timeout)) {
//                submitEmptyResponse(asyncResponse);
//            }
//        }
//    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Response insert(String guid, DeviceNotificationWrapper notificationSubmit) {
        logger.debug("DeviceNotification insert requested: {}", notificationSubmit);

        HivePrincipal principal = (HivePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (notificationSubmit == null || notificationSubmit.getNotification() == null) {
            logger.warn("DeviceNotification insert proceed with error. BAD REQUEST: notification is required.");
            ErrorResponse errorResponseEntity = new ErrorResponse(BAD_REQUEST.getStatusCode(),
                    Messages.INVALID_REQUEST_PARAMETERS);
            return ResponseFactory.response(BAD_REQUEST, errorResponseEntity);
        }
        DeviceVO device = deviceService.findByGuidWithPermissionsCheck(guid, principal);
        if (device == null) {
            logger.warn("DeviceNotification insert proceed with error. NOT FOUND: device {} not found.", guid);
            return ResponseFactory.response(NOT_FOUND, new ErrorResponse(NOT_FOUND.getStatusCode(),
                    String.format(Messages.DEVICE_NOT_FOUND, guid)));
        }
        if (device.getNetwork() == null) {
            logger.warn("DeviceNotification insert proceed with error. FORBIDDEN: Device {} is not connected to network.", guid);
            return ResponseFactory.response(FORBIDDEN, new ErrorResponse(FORBIDDEN.getStatusCode(),
                    String.format(Messages.DEVICE_IS_NOT_CONNECTED_TO_NETWORK, guid)));
        }
        DeviceNotification message = notificationService.convertToMessage(notificationSubmit, device);
        notificationService.submitDeviceNotification(message, device);

        logger.debug("DeviceNotification insertAll proceed successfully");
        return ResponseFactory.response(CREATED, message, NOTIFICATION_TO_DEVICE);
    }

    private void submitEmptyResponse(final AsyncResponse asyncResponse) {
        asyncResponse.resume(ResponseFactory.response(Response.Status.OK, Collections.emptyList(),
                JsonPolicyDef.Policy.NOTIFICATION_TO_CLIENT));
    }

//    private NotificationSubscription getNotificationInsertSubscription(HivePrincipal principal, String guid, UUID reqId, String names,
//                                                                       AsyncResponse asyncResponse, boolean isMany, FutureTask<Void> waitTask) {
//        return new NotificationSubscription(principal, guid, reqId, names,
//                RestHandlerCreator.createNotificationInsert(asyncResponse, isMany, waitTask));
//    }
}
