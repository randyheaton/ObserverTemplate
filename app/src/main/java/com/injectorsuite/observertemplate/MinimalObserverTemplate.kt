package com.injectorsuite.observertemplate

import kotlinx.serialization.Serializable


/*
 *
 *
 * The long and short of what's going on here is we want to be able to implement an observer pattern where request senders and update receivers
 * can be defined with an abbreviated, descriptive, readable syntax that allows very minimal and very targeted framework magic to work
 * out the implementation details, with the end goal that there's high readability with close to zero hidden logic.
 *
 * So we hide a little bit of logic behind framework magic in order to work out how broadcast receivers are registered to the central message sender.
 * And we hide a few lines of logic behind framework magic that specifies how request senders have their requests received by the central request receiver.
 *
 * And in return, you can use the syntax 'class ViewModel: DoesReceiveBroadcasts(), CanDispatchRequestsOfType<RequestType1>' to lay out the majority of a ViewModel's behaviors.
 *
 *
 *
 */


/**
 * The BroadcastType sealed class provides a single source of truth for all possible
 * state updates a view might be interested to register for.
 */
sealed class BroadcastType {
    class BroadcastType1: BroadcastType()
    class BroadcastType2: BroadcastType()
}


/*
    * The RequestType sealed class is a container for all requests that might be made
    * by a ViewModel on behalf of a view.
    *
    * Here they are provided as @Serializable because they will most likely be rendered at some point as POST payloads
    * by a client called by RepositoryManager
    *
    * AggregateRequest is probably the best that you could ask for in terms of succinctly representing the need of a requester
    * that is responsible for making requests of three or more types. The reason for that has to do with fundamental properties of generics
    * that are hard-baked into the language.
    *
    * It's not possible to facilitate a syntax like 'class Requestor: CanDispatchRequestsOfTypes<RequestType1, RequestType2, RequestType3>' with
    * a variable number of type parameters. Type erasure creates a wall where specifying behaviors in the manner that we're going for while also
    * having extenders/implementers not bear the implementation burden is just not very feasible.
    *
    * So creating an AggregateRequest class is a pretty great way to compensate for that and get a result that's expressive and very low on typed characters.
 */
sealed class RequestType {
    @Serializable
    data class RequestType1(val prop1: String): RequestType()
    @Serializable
    data class RequestType2(val prop2: String): RequestType()
    @Serializable
    data class AggregateRequest(
        val requestType1: RequestType1? = null,
        val requestType2: RequestType2? = null
    ) : RequestType() {
        fun withRequestType1(requestType1: RequestType1): AggregateRequest {
            return this.copy(requestType1 = requestType1)
        }

        fun withRequestType2(requestType2: RequestType2): AggregateRequest {
            return this.copy(requestType2 = requestType2)
        }
    }
}

/*
  * The RepositoryManager can be a singleton if you'd like, or if you're going to end up needing a Context, you can
  * bundle its state into a companion object and leave its Context consuming methods out to be called per instance, so that it
  * can function as a singleton without storing a Context.
  *
  * The RepositoryManager is responsible for broadcasting state updates to all observers that have registered to receive them. That's
  * probably going to mean ViewModels.
  *
  * It works well to delegate all state altering behaviors to the RepositoryManager, let it decide when to make http requests, let it read and
  * write from a local db, prefs, or flat JSON files, and just generally function as the brains when it comes to state mutations.
  *
  * It should be sufficient to have any helper managers (http, IO, etc) implemented as singletons and have
  * the RepositoryManager pass a Context to them if they need.
 */
class RepositoryManager {
    companion object {
        var subscribers: List<DoesReceiveBroadcasts> = listOf()
    }

    fun broadcastTo(broadcastType: BroadcastType, subscriber: DoesReceiveBroadcasts) {
        when (broadcastType) {
            is BroadcastType.BroadcastType1 -> {
                subscriber.receiveBroadcastOfType(broadcastType)
            }
            is BroadcastType.BroadcastType2 -> {
                subscriber.receiveBroadcastOfType(broadcastType)
            }
        }
    }

    fun broadcast(broadcastType: BroadcastType) {
        subscribers.forEach{
            broadcastTo(broadcastType, it)
        }
    }

    /**
     * This is the glue that binds subscribers to the RepositoryManager so that they can receive broadcasts by having their registered callbacks invoked on their
     * behalf.
     *
     * It works well to have the registered callbacks invoked immediately upon registration so that listeners can be provided with the latest states.
     *
     * But you may want to have a separate implementation that only serves on legitimate change events.
     */

    fun addAndServeImmediately(subscriber: DoesReceiveBroadcasts) {
        subscribers = subscribers + subscriber
        listOf(BroadcastType.BroadcastType1(), BroadcastType.BroadcastType2()).forEach {
            broadcastTo(it, subscriber)
        }
    }


    /**
     *
     * This is probably where you'll want to make http requests and create side effects to the global state of the business.
     */

    fun handleForwardedDispatch(requestType: RequestType) {
        when (requestType) {
            is RequestType.RequestType1 -> {
                println("RequestType1")
            }
            is RequestType.RequestType2 -> {
                println("RequestType2")
                broadcast(BroadcastType.BroadcastType1())

            }
            is RequestType.AggregateRequest -> {
                requestType.requestType1?.let {
                    handleForwardedDispatch(it)
                }
                requestType.requestType2?.let {
                    handleForwardedDispatch(it)
                }
            }
        }
    }
}

/**
 *
 *
 * The few lines of not overt logic/ framework magic that lets broadcast receivers register with the RepositoryManager without having to know the details and only specify the callbacks that
 * will be invoked on their behalf.
 *
 */

abstract class DoesReceiveBroadcasts {
    init {
        /*
        * Instantiation of the extender *will* fully resolve on the thread in which it is created
        * prior to the Serve-Immediately action on that same thread.
        * So in this context, the exposure of 'this' is only unsafe in the sense that an extender that
        * is not fully instantiated may not receive a broadcast incidentally in progress, which is expected
        * as the extender is not yet subscribed for such a broadcast and will receive its own from the
        * Serve-Immediately action.
         */
        RepositoryManager().addAndServeImmediately(this)
    }
    open fun receiveBroadcastOfType(broadcastType1: BroadcastType.BroadcastType1) {

    }
    open fun receiveBroadcastOfType(broadcastType2: BroadcastType.BroadcastType2) {

    }
}


/**
 * A few lines of not overt logic/ framework magic that gives requesters a passthrough to the RepositoryManager without having to do the plumbing themselves.
 *
 *
 * Empty interfaces with separate extension functions are a good way to provide functions for implementers without advertising overrides that are not supposed to be availed.
 */
interface CanDispatchRequestsOfType<T:RequestType>

/**
 *
 * Some uses of this template might benefit from removing this option. But for instances where AggregateRequest is overkill, this could be
 * a lightweight way to allow a requester to make more than one type of request.
 *
 */
interface AndOfSecondType<T:RequestType>

inline fun<reified T:RequestType>  AndOfSecondType<T>.dispatchRequest(requestType: T) {
    RepositoryManager().handleForwardedDispatch(requestType)
}

inline fun<reified T:RequestType>  CanDispatchRequestsOfType<T>.dispatchRequest(requestType: T) {
    RepositoryManager().handleForwardedDispatch(requestType)
}



/**
 *
 * Some example usage; check MainActivity for the calls.
 *
 */



/**
 * A ViewModel that can dispatch two types of requests but doesn't have to explain how. It makes provisions for receiving one type of broadcast, and is responsible
 * for specifying how it will respond to the state changes that broadcast represents.
 */
class ViewModel1: DoesReceiveBroadcasts(), CanDispatchRequestsOfType<RequestType.RequestType1>, AndOfSecondType<RequestType.RequestType2> {
    override fun receiveBroadcastOfType(broadcastType1: BroadcastType.BroadcastType1) {
        println("ViewModel1 received BroadcastType1")
    }
}

/**
 *
 * We want to see how this plays out for the example, but specifying the form of requests and providing a friendly name for the View to pass through for dispatchRequest would all
 * be handled in the ViewModel. In practice, passthroughs to the ViewModel's dispatchRequest would look more like: 'viewModel1.onTextFieldSubmit(string)' or 'viewModel1.onButtonPress()'
 */
class View1 () {
    val viewModel1 = ViewModel1()
    fun dispatchRequest1() {
        viewModel1.dispatchRequest(RequestType.RequestType1(""))
    }
    fun dispatchRequest2() {
        viewModel1.dispatchRequest(RequestType.RequestType2(""))
    }
}

/**
 * A ViewModel that can dispatch two types of requests but doesn't have to explain how. It makes provisions for receiving one type of broadcast, and is responsible
 * for specifying how it will respond to the state changes that broadcast represents. Here we're using AggregateRequest. For large ViewModels, that's probably going to be
 * the only way to avail dispatchRequest for the required types.
 */

class ViewModel2: DoesReceiveBroadcasts(), CanDispatchRequestsOfType<RequestType.AggregateRequest> {
    override fun receiveBroadcastOfType(broadcastType2: BroadcastType.BroadcastType2) {
        println("ViewModel2 received BroadcastType2")
    }
}

/**
 *
 * We want to see how this plays out for the example, but specifying the form of requests and providing a friendly name for the View to pass through for dispatchRequest would all
 * be handled in the ViewModel. In practice, passthroughs to the ViewModel's dispatchRequest would look more like: 'viewModel1.onTextFieldSubmit(string)' or 'viewModel1.onButtonPress()'
 *
 * You'd want the implementer of CanDispatchRequestsOfType to be the one responsible for knowing AggregateRequest's builder pattern.
 */

class View2() {
    val viewModel2 = ViewModel2()
    fun dispatchRequest() {
        viewModel2.dispatchRequest(RequestType.AggregateRequest().withRequestType1(RequestType.RequestType1("")).withRequestType2(RequestType.RequestType2("")))
    }
}