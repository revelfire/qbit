/*
 * Copyright (c) 2015. Rick Hightower, Geoff Chandler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * QBit - The Microservice lib for Java : JSON, WebSocket, REST. Be The Web!
 */

package io.advantageous.qbit.example.events;

import io.advantageous.qbit.QBit;
import io.advantageous.qbit.annotation.EventChannel;
import io.advantageous.qbit.annotation.OnEvent;
import io.advantageous.qbit.annotation.QueueCallback;
import io.advantageous.qbit.annotation.QueueCallbackType;
import io.advantageous.qbit.events.EventBusProxyCreator;
import io.advantageous.qbit.events.EventManager;
import io.advantageous.qbit.service.ServiceQueue;
import io.advantageous.boon.core.Sys;

import static io.advantageous.qbit.service.ServiceBuilder.serviceBuilder;
import static io.advantageous.qbit.service.ServiceProxyUtils.flushServiceProxy;

/**
 * EmployeeEventExampleUsingChannelsToSendEvents
 * Created by rhightower on 2/11/15.
 */
public class EmployeeEventExampleUsingChannelsToSendEvents {


    public static final String NEW_HIRE_CHANNEL = "com.mycompnay.employee.new";


    public static void main(String... args) {


        /* Create you own private event bus. */
        EventManager privateEventBus = QBit.factory().createEventManager("foo");

        /* Create a service queue for this event bus. */
        ServiceQueue privateEventBusServiceQueue = serviceBuilder()
                .setServiceObject(privateEventBus)
                .setInvokeDynamic(false).build();


        final EventBusProxyCreator eventBusProxyCreator =
                QBit.factory().eventBusProxyCreator();

        final EmployeeEventManager employeeEventManager =
                eventBusProxyCreator.createProxy(privateEventBus, EmployeeEventManager.class);

        final SalaryChangedChannel salaryChangedChannel = eventBusProxyCreator.createProxy(privateEventBus, SalaryChangedChannel.class);

        /*
        Create your EmployeeHiringService but this time pass the private event bus.
        Note you could easily use Spring or Guice for this wiring.
         */
        EmployeeHiringService employeeHiring = new EmployeeHiringService(employeeEventManager, salaryChangedChannel);



        /* Now createWithWorkers your other service POJOs which have no compile time dependencies on QBit. */
        PayrollService payroll = new PayrollService();
        BenefitsService benefits = new BenefitsService();
        VolunteerService volunteering = new VolunteerService();


        /** Employee hiring service. */
        ServiceQueue employeeHiringServiceQueue = serviceBuilder()
                .setServiceObject(employeeHiring)
                .setInvokeDynamic(false).build();
        /** Payroll service */
        ServiceQueue payrollServiceQueue = serviceBuilder()
                .setServiceObject(payroll)
                .setInvokeDynamic(false).build();
        /** Employee Benefits service. */
        ServiceQueue employeeBenefitsServiceQueue = serviceBuilder()
                .setServiceObject(benefits)
                .setInvokeDynamic(false).build();
        /* Community outreach program. */
        ServiceQueue volunteeringServiceQueue = serviceBuilder()
                .setServiceObject(volunteering)
                .setInvokeDynamic(false).build();


        /* Now wire in the event bus so it can fire events into the service queues. */
        privateEventBus.joinService(payrollServiceQueue);
        privateEventBus.joinService(employeeBenefitsServiceQueue);
        privateEventBus.joinService(volunteeringServiceQueue);


        privateEventBusServiceQueue.start();
        employeeHiringServiceQueue.start();
        volunteeringServiceQueue.start();
        payrollServiceQueue.start();
        employeeBenefitsServiceQueue.start();


        /** Now createWithWorkers the service proxy like before. */
        EmployeeHiringServiceClient employeeHiringServiceClientProxy =
                employeeHiringServiceQueue.createProxy(EmployeeHiringServiceClient.class);

        /** Call the hireEmployee method which triggers the other events. */
        employeeHiringServiceClientProxy.hireEmployee(new Employee("Rick", 1));

        flushServiceProxy(employeeHiringServiceClientProxy);

        Sys.sleep(5_000);

    }

    interface EmployeeHiringServiceClient {
        void hireEmployee(final Employee employee);

    }



    @EventChannel
    interface SalaryChangedChannel {


        void salaryChanged(Employee employee, int newSalary);

    }


    interface EmployeeEventManager {

        @EventChannel(NEW_HIRE_CHANNEL)
        void sendNewEmployee(Employee employee);



    }

    public static class Employee {
        final String firstName;
        final int employeeId;

        public Employee(String firstName, int employeeId) {
            this.firstName = firstName;
            this.employeeId = employeeId;
        }

        public String getFirstName() {
            return firstName;
        }

        public int getEmployeeId() {
            return employeeId;
        }

        @Override
        public String toString() {
            return "Employee{" +
                    "firstName='" + firstName + '\'' +
                    ", employeeId=" + employeeId +
                    '}';
        }
    }

    public static class EmployeeHiringService {

        final EmployeeEventManager eventManager;
        final SalaryChangedChannel salaryChangedChannel;

        public EmployeeHiringService(final EmployeeEventManager employeeEventManager,
                                     final SalaryChangedChannel salaryChangedChannel) {
            this.eventManager = employeeEventManager;
            this.salaryChangedChannel = salaryChangedChannel;
        }


        @QueueCallback(QueueCallbackType.EMPTY)
        private void noMoreRequests() {


            flushServiceProxy(salaryChangedChannel);
            flushServiceProxy(eventManager);
        }


        @QueueCallback(QueueCallbackType.LIMIT)
        private void hitLimitOfRequests() {

            flushServiceProxy(salaryChangedChannel);
            flushServiceProxy(eventManager);
        }


        public void hireEmployee(final Employee employee) {

            int salary = 100;
            System.out.printf("Hired employee %s\n", employee);

            //Does stuff to hire employee


            eventManager.sendNewEmployee(employee);
            salaryChangedChannel.salaryChanged(employee, salary);


        }

    }

    public static class BenefitsService {

        @OnEvent(NEW_HIRE_CHANNEL)
        public void enroll(final Employee employee) {

            System.out.printf("Employee enrolled into benefits system employee %s %d\n",
                    employee.getFirstName(), employee.getEmployeeId());

        }

    }

    public static class VolunteerService {

        @OnEvent(NEW_HIRE_CHANNEL)
        public void invite(final Employee employee) {

            System.out.printf("Employee will be invited to the community outreach program %s %d\n",
                    employee.getFirstName(), employee.getEmployeeId());

        }

    }

    public static class PayrollService implements SalaryChangedChannel{

        @Override
        public void salaryChanged(Employee employee, int newSalary) {
            System.out.printf("DIRECT FROM CHANNEL SalaryChangedChannel Employee added to payroll  %s %d %d\n",
                    employee.getFirstName(), employee.getEmployeeId(), newSalary);

        }
    }
}
