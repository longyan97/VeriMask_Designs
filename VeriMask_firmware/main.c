/*
Use Segger Embedded Studio to handle the project.
The nRF board is PCA10100 (nRF52833)
You need to download the SDK. We used SDK 17
!!! Please put this whole prject folder under the /sdk/example/peripherals/ folder !!!
*/


#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include "app_error.h"
#include "app_timer.h"
#include "app_util_platform.h"
#include "boards.h" 
#include "ble_advdata.h"
#include "ble_conn_params.h"
#include "bsp.h"
#include "nordic_common.h"
#include "nrf_delay.h"
#include "nrf_gpio.h"
#include "nrf_log.h"
#include "nrf_log_ctrl.h"
#include "nrf_log_default_backends.h"
#include "nrf_sdh.h"
#include "nrf_soc.h"
#include "nrf_sdh.h"
#include "nrf_sdh_ble.h"
#include "nrf_gpiote.h"
#include "nrf.h"
#include "nrf_temp.h"
#include "nrf_pwr_mgmt.h"
#include "nrf_drv_gpiote.h"
#include "sdk_config.h"
#include "nrf_drv_twi.h" 

                                                             
#define NODEID                          24                                                                 
//#define CONNECTABLE_BLE
#define SENSOR_7021
//#define SENSOR_SHT85
#define LED_SENSOR_COMBINE                                                // handle led blinking and sensor reading in one handler
#ifdef LED_SENSOR_COMBINE
  int blink_count =1;                                                   // equals sensor_led_ratio 
  int sensor_led_ratio = 1;                                              // equals ADV_INTERVAL_MS / LED_INTERVAL_MS
#endif 
#define LED_FLASH_TIME_MS               5                                 // ms each time
#define LED_FLASH_PIN                   15                                 // V2
#define LED_INTERVAL_MS                 10000                              // LED bliking interval ms
#define ADV_INTERVAL_MS                 10000                             // broadcasting interval for each emulated node 
#define SENSOR_INTERVAL_MS              10000                             // sensor data acquisition interval for each emulated node 
#define EMULATE_NODE_NUM                1                                 // The number of nodes this single hardware node should emulate

#define SENSORADDR7021      0X40          // I2C addr for SI7021. Remember to set the right slave addr when init!
#define SHTC85_SENSORADDR   0x44          // I2C addr for SHTC85
#define RH_READ             0xE5          // I2C relative humidity register/cmd for SI7021
#define POST_RH_TEMP_READ   0xE0          // I2C temperature register/cmd for SI7021 (quick version)
#define SHTC85_READ         0x2416        // I2C high repeatability 1mps reading condition
/* TWI instance ID. */
#define TWI_INSTANCE_ID     1             // Gotta use instance 1 instead of 0 to avoid collision with SPI config. sdk_config.h should be changed accordingly




/*
***************************************************************************************************
************************ Start of BLE stuff *******************************************************
*/

#define APP_BLE_CONN_CFG_TAG            1                                  /**< A tag identifying the SoftDevice BLE configuration. */
#define NON_CONNECTABLE_ADV_INTERVAL    MSEC_TO_UNITS(ADV_INTERVAL_MS, UNIT_0_625_MS)  /**< The advertising interval for non-connectable advertisement (10000 ms). This value can vary between 100ms to 10.24s). */
#define APP_BEACON_INFO_LENGTH          0x09                               /**< Total length of information advertised by the Beacon. */
#define APP_COMPANY_IDENTIFIER          0x00ff                             /**< Company identifier for Nordic Semiconductor ASA. as per www.bluetooth.org. */
#define DEAD_BEEF                       0xDEADBEEF                         /**< Value used as error code on stack dump, can be used to identify stack location on stack unwind. */


#define FIRST_CONN_PARAMS_UPDATE_DELAY  APP_TIMER_TICKS(20000)                  /**< Time from initiating event (connect or start of notification) to first time sd_ble_gap_conn_param_update is called (15 seconds). */
#define NEXT_CONN_PARAMS_UPDATE_DELAY   APP_TIMER_TICKS(5000)                   /**< Time between each call to sd_ble_gap_conn_param_update after the first call (5 seconds). */
#define MAX_CONN_PARAMS_UPDATE_COUNT    3                                       /**< Number of attempts before giving up the connection parameter negotiation. */
#define APP_BLE_OBSERVER_PRIO           3                                       /**< Application's BLE observer priority. You shouldn't need to modify this value. */
#define APP_ADV_INTERVAL                MSEC_TO_UNITS(ADV_INTERVAL_MS, UNIT_0_625_MS)                                      /**< The advertising interval (in units of 0.625 ms; this value corresponds to 40 ms). */
#define APP_ADV_DURATION                BLE_GAP_ADV_TIMEOUT_GENERAL_UNLIMITED   /**< The advertising time-out (in units of seconds). When set to 0, we will never time out. */

static uint16_t m_conn_handle = BLE_CONN_HANDLE_INVALID;                        /**< Handle of the current connection. */
static const nrf_drv_twi_t m_twi = NRF_DRV_TWI_INSTANCE(TWI_INSTANCE_ID);


// Used to store the data to send. First byte is the node number 
uint8_t sensor_reading[APP_BEACON_INFO_LENGTH] = {
    NODEID, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00              
};                                                 


// Some control variables
int count = 1;
int test = 0;

// ble related variables
static ble_gap_adv_params_t m_adv_params;                                  /**< Parameters to be passed to the stack when starting advertising. */
static uint8_t              m_adv_handle = BLE_GAP_ADV_SET_HANDLE_NOT_SET; /**< Advertising handle used to identify an advertising set. */
static uint8_t              m_enc_advdata[BLE_GAP_ADV_SET_DATA_SIZE_MAX];  /**< Buffer for storing an encoded advertising set. */
/**@brief Struct that contains pointers to the encoded advertising data. */
static ble_gap_adv_data_t m_adv_data =
{
    .adv_data =
    {
        .p_data = m_enc_advdata,
        .len    = BLE_GAP_ADV_SET_DATA_SIZE_MAX
    },
    .scan_rsp_data =
    {
        .p_data = NULL,
        .len    = 0

    }
};


void sensor_data_set(bool init){
    // debugging use: calc readings, emulate more nodes
    if(!init){

      if (EMULATE_NODE_NUM > 1)
      {
        test ++;
        if(test % EMULATE_NODE_NUM == 0){
          sensor_reading[0] = 0;
        }
        else{
          sensor_reading[0]++;
        }
      }
    }
    uint32_t      err_code;
    ble_advdata_t advdata;
#ifdef  CONNECTABLE_BLE
    uint8_t       flags = BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE;
#else
    uint8_t       flags = BLE_GAP_ADV_FLAG_BR_EDR_NOT_SUPPORTED;
#endif
    ble_advdata_manuf_data_t manuf_specific_data;
    manuf_specific_data.company_identifier = APP_COMPANY_IDENTIFIER;
    manuf_specific_data.data.p_data = (uint8_t *) sensor_reading;
    manuf_specific_data.data.size   = APP_BEACON_INFO_LENGTH;

    // Build and set advertising data.
    memset(&advdata, 0, sizeof(advdata));
    advdata.name_type             = BLE_ADVDATA_NO_NAME;
    #ifdef  CONNECTABLE_BLE
    advdata.include_appearance    = true;
    #endif
    advdata.flags                 = flags ;
    advdata.p_manuf_specific_data = &manuf_specific_data;

    err_code = ble_advdata_encode(&advdata, m_adv_data.adv_data.p_data, &m_adv_data.adv_data.len);
    APP_ERROR_CHECK(err_code);
}

/**@brief Function for initializing the Advertising functionality.
 *
 * @details Encodes the required advertising data and passes it to the stack.
 *          Also builds a structure to be passed to the stack when starting advertising.
 */
static void advertising_init(void)
{
    uint32_t      err_code;
    sensor_data_set(true);

    // Initialize advertising parameters (used when starting advertising).
    memset(&m_adv_params, 0, sizeof(m_adv_params));

#ifdef CONNECTABLE_BLE
    m_adv_params.properties.type = BLE_GAP_ADV_TYPE_CONNECTABLE_SCANNABLE_UNDIRECTED;
    m_adv_params.p_peer_addr     = NULL;    // Undirected advertisement.
    m_adv_params.filter_policy   = BLE_GAP_ADV_FP_ANY;
    m_adv_params.interval        = APP_ADV_INTERVAL;
    m_adv_params.primary_phy     = BLE_GAP_PHY_1MBPS;
    m_adv_params.duration        = APP_ADV_DURATION;
#else
    m_adv_params.properties.type = BLE_GAP_ADV_TYPE_NONCONNECTABLE_NONSCANNABLE_UNDIRECTED;
    m_adv_params.p_peer_addr     = NULL;    // Undirected advertisement.
    m_adv_params.filter_policy   = BLE_GAP_ADV_FP_ANY;
    m_adv_params.interval        = NON_CONNECTABLE_ADV_INTERVAL;
    m_adv_params.duration        = 0;       // Never time out.

#endif


    err_code = sd_ble_gap_adv_set_configure(&m_adv_handle, &m_adv_data, &m_adv_params);
    APP_ERROR_CHECK(err_code);
}


/**@brief Function for starting advertising.
 */
static void advertising_start(void)
{
    ret_code_t err_code;

    err_code = sd_ble_gap_adv_start(m_adv_handle, APP_BLE_CONN_CFG_TAG);
    APP_ERROR_CHECK(err_code);

    err_code = bsp_indication_set(BSP_INDICATE_ADVERTISING);
    APP_ERROR_CHECK(err_code);
}

/**@brief Function for stopping advertising.
 */
static void advertising_stop(void)
{
    ret_code_t err_code;

    err_code = sd_ble_gap_adv_stop(m_adv_handle);
    APP_ERROR_CHECK(err_code);
}

/**@brief Function for handling BLE events.
 *
 * @param[in]   p_ble_evt   Bluetooth stack event.
 * @param[in]   p_context   Unused.
 */
static void ble_evt_handler(ble_evt_t const * p_ble_evt, void * p_context)
{
    ret_code_t err_code;

    switch (p_ble_evt->header.evt_id)
    {
        case BLE_GAP_EVT_CONNECTED:
            //NRF_LOG_INFO("Connected\n");
            m_conn_handle = p_ble_evt->evt.gap_evt.conn_handle;
            break;

        case BLE_GAP_EVT_DISCONNECTED:
            //NRF_LOG_INFO("Disconnected\n");
            m_conn_handle = BLE_CONN_HANDLE_INVALID;
            advertising_start();
            break;
        default:
            // No implementation needed.
            break;
    }


}

/**@brief Function for initializing the BLE stack.
 *
 * @details Initializes the SoftDevice and the BLE event interrupt.
 */
static void ble_stack_init(void)
{
    ret_code_t err_code;

    err_code = nrf_sdh_enable_request();
    APP_ERROR_CHECK(err_code);

    // Configure the BLE stack using the default settings.
    // Fetch the start address of the application RAM.
    uint32_t ram_start = 0;
    err_code = nrf_sdh_ble_default_cfg_set(APP_BLE_CONN_CFG_TAG, &ram_start);
    APP_ERROR_CHECK(err_code);

    // Enable BLE stack.
    err_code = nrf_sdh_ble_enable(&ram_start);
    APP_ERROR_CHECK(err_code);

    #ifdef CONNECTABLE_BLE
    // Register a handler for BLE events.
    NRF_SDH_BLE_OBSERVER(m_ble_observer, APP_BLE_OBSERVER_PRIO, ble_evt_handler, NULL);
    #endif
}


/**@brief Function for handling the Connection Parameters Module.
 *
 * @details This function will be called for all events in the Connection Parameters Module that
 *          are passed to the application.
 *
 * @note All this function does is to disconnect. This could have been done by simply
 *       setting the disconnect_on_fail config parameter, but instead we use the event
 *       handler mechanism to demonstrate its use.
 *
 * @param[in] p_evt  Event received from the Connection Parameters Module.
 */
static void on_conn_params_evt(ble_conn_params_evt_t * p_evt)
{
    ret_code_t err_code;

    if (p_evt->evt_type == BLE_CONN_PARAMS_EVT_FAILED)
    {
        err_code = sd_ble_gap_disconnect(m_conn_handle, BLE_HCI_CONN_INTERVAL_UNACCEPTABLE);
        APP_ERROR_CHECK(err_code);
    }
}


/**@brief Function for handling a Connection Parameters error.
 *
 * @param[in] nrf_error  Error code containing information about what went wrong.
 */
static void conn_params_error_handler(uint32_t nrf_error)
{
    APP_ERROR_HANDLER(nrf_error);
}

/**@brief Function for initializing the Connection Parameters module.
 */
static void conn_params_init(void)
{
    ret_code_t             err_code;
    ble_conn_params_init_t cp_init;

    memset(&cp_init, 0, sizeof(cp_init));

    cp_init.p_conn_params                  = NULL;
    cp_init.first_conn_params_update_delay = FIRST_CONN_PARAMS_UPDATE_DELAY;
    cp_init.next_conn_params_update_delay  = NEXT_CONN_PARAMS_UPDATE_DELAY;
    cp_init.max_conn_params_update_count   = MAX_CONN_PARAMS_UPDATE_COUNT;
    cp_init.start_on_notify_cccd_handle    = BLE_GATT_HANDLE_INVALID;
    cp_init.disconnect_on_fail             = false;
    cp_init.evt_handler                    = on_conn_params_evt;
    cp_init.error_handler                  = conn_params_error_handler;

    err_code = ble_conn_params_init(&cp_init);
    APP_ERROR_CHECK(err_code);
}

/*
***************************************************************************************************
************************** end of BLE stuff *******************************************************
*/








/*
***************************************************************************************************
************************** start of timer stuff ***************************************************
*/


// define the timers 
APP_TIMER_DEF(m_sensor_reading_timer);
APP_TIMER_DEF(m_led_timer);
APP_TIMER_DEF(m_led_blinkdelay_timer); 
APP_TIMER_DEF(m_sht85_readdelay_timer); 

// timer handlers
void read_sensor_handler(void * p_context)
{
        #ifdef SENSOR_7021
        I2C_ReadSI7021();  
        #endif
        #ifdef SENSOR_SHT85
        I2C_Read_SHT85();
        #endif 
        
}
void ledblink_handler(void * p_context)
{
     // flash the LED here 
        ret_code_t err_code;
        nrf_gpio_pin_set(LED_FLASH_PIN);
        err_code = app_timer_start(m_led_blinkdelay_timer, APP_TIMER_TICKS(LED_FLASH_TIME_MS), NULL); 
        APP_ERROR_CHECK(err_code);

#ifdef LED_SENSOR_COMBINE
        if (blink_count == sensor_led_ratio)
        {
            #ifdef SENSOR_7021
            I2C_ReadSI7021();  
            #endif
            #ifdef SENSOR_SHT85
            I2C_Read_SHT85();
            #endif
            blink_count = 1;
        } else {
            blink_count ++;
        }
#endif 
}



/**@brief Function for initializing the timer module.
 */
void led_delay_handler (void * p_context)
{
    nrf_gpio_pin_clear(LED_FLASH_PIN); // turn off the flashing LED
}

void sht85_delay_handler (void * p_context)
{
    ret_code_t err_code;
    uint8_t temp[6] = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    err_code = nrf_drv_twi_rx(&m_twi, SHTC85_SENSORADDR,  &temp[0], 6);
    APP_ERROR_CHECK(err_code);
    sensor_reading[7] = temp[0];
    sensor_reading[8] = temp[1];
    sensor_reading[5] = temp[3];
    sensor_reading[6] = temp[4];
    sensor_data_set(false);

}


static void timers_init_start(void)
{
    ret_code_t err_code = app_timer_init();
    APP_ERROR_CHECK(err_code);
    err_code = app_timer_create(&m_sensor_reading_timer, APP_TIMER_MODE_REPEATED, read_sensor_handler);
    APP_ERROR_CHECK(err_code);
    err_code = app_timer_create(&m_led_timer, APP_TIMER_MODE_REPEATED, ledblink_handler);
    APP_ERROR_CHECK(err_code);
    err_code = app_timer_create(&m_led_blinkdelay_timer, APP_TIMER_MODE_SINGLE_SHOT, led_delay_handler);
    APP_ERROR_CHECK(err_code);
    err_code = app_timer_create(&m_sht85_readdelay_timer, APP_TIMER_MODE_SINGLE_SHOT, sht85_delay_handler);
    APP_ERROR_CHECK(err_code);

#ifndef LED_SENSOR_COMBINE
    err_code = app_timer_start(m_sensor_reading_timer, APP_TIMER_TICKS(SENSOR_INTERVAL_MS), NULL);
    APP_ERROR_CHECK(err_code);
#endif

    err_code = app_timer_start(m_led_timer, APP_TIMER_TICKS(LED_INTERVAL_MS), NULL);
    APP_ERROR_CHECK(err_code);
}

/*
***************************************************************************************************
**************************** end of timer stuff ***************************************************
*/








/*
***************************************************************************************************
**************************** start of I2C stuff ***************************************************
*/


/**
 * @brief I2C initialization.
 */
void twi_init (void)
{
    ret_code_t err_code;

    // PIN27 as CLK
    // PIN26 as DATA
    // PIN16 as VCC
    // No external pull-up resistors needed because pins are internally pulled-up by default

    // VCC for the sensor
#ifdef SENSOR_7021
    nrf_gpio_cfg_output(16);
    nrf_gpio_pin_set(16);
#endif
#ifdef SENSOR_SHT85
    nrf_gpio_cfg_output(17);
    nrf_gpio_pin_set(17);
#endif
    const nrf_drv_twi_config_t twi_lm75b_config = {
       .scl                = ARDUINO_SCL_PIN,                     
       .sda                = ARDUINO_SDA_PIN,                     
       .frequency          = NRF_DRV_TWI_FREQ_100K,
       .interrupt_priority = APP_IRQ_PRIORITY_HIGH,
       .clear_bus_init     = false
    };



    // No TWI transfer event handler function provided so it's gonna run in blocking mode 
    err_code = nrf_drv_twi_init(&m_twi, &twi_lm75b_config, NULL, NULL);
    APP_ERROR_CHECK(err_code);

    nrf_drv_twi_enable(&m_twi);

}


// Read sensor data (4 bytes in total) from SI7021 via I2C
void I2C_ReadSI7021(void)
{

    ret_code_t err_code;
    uint8_t temp[4] = {0x00, 0x00, 0x00, 0x00}; 
    // Read relative humidity
    uint8_t cmd = RH_READ;
    err_code = nrf_drv_twi_tx(&m_twi, SENSORADDR7021, &cmd, 1, false);
    APP_ERROR_CHECK(err_code);
    err_code = nrf_drv_twi_rx(&m_twi, SENSORADDR7021,  &(temp), 2);
    APP_ERROR_CHECK(err_code);

    // Read temperature
    cmd = POST_RH_TEMP_READ;
    err_code = nrf_drv_twi_tx(&m_twi, SENSORADDR7021, &cmd, 1, false);
    APP_ERROR_CHECK(err_code);
    err_code = nrf_drv_twi_rx(&m_twi, SENSORADDR7021, &(temp[2]), 2);
    APP_ERROR_CHECK(err_code);

    sensor_reading[1] = temp[0];
    sensor_reading[2] = temp[1];
    sensor_reading[3] = temp[2];
    sensor_reading[4] = temp[3];
    sensor_data_set(false);

}



uint8_t crc8(const uint8_t *data, uint8_t len)
{
  // adapted from SHT21 sample code from
  // http://www.sensirion.com/en/products/humidity-temperature/download-center/

  uint8_t crc = 0xff;
  uint8_t byteCtr;
  for (byteCtr = 0; byteCtr < len; ++byteCtr) {
    crc ^= data[byteCtr];
    for (uint8_t bit = 8; bit > 0; --bit) {
      if (crc & 0x80) {
        crc = (crc << 1) ^ 0x31;
      } else {
        crc = (crc << 1);
      }
    }
  }
  return crc;
}


// Read sensor data (4 bytes in total) from SI7021 via I2C
void I2C_Read_SHT85(void)
{
    ret_code_t err_code;
    // Read relative humidity
    uint8_t cmd[2] = {0x00, 0x00}; 
    cmd[0] = SHTC85_READ >> 8;
    cmd[1] = SHTC85_READ & 0xff;
    err_code = nrf_drv_twi_tx(&m_twi, SHTC85_SENSORADDR, &cmd, 2, false);
     APP_ERROR_CHECK(err_code);

//    // The following readout cmd is only for periodic acquisition 
//    cmd[0] = 0XE000 >> 8;
//    cmd[1] = 0XE000 & 0xff;
//    err_code = nrf_drv_twi_tx(&m_twi, SHTC85_SENSORADDR, &cmd, 2, false);

    err_code = app_timer_start(m_sht85_readdelay_timer, APP_TIMER_TICKS(5), NULL);
    APP_ERROR_CHECK(err_code);
    idle_state_handle();

}

/*
***************************************************************************************************
****************************** end of I2C stuff ***************************************************
*/











/*
***************************************************************************************************
****************************** start of misc stuff ************************************************
*/


/**@brief Function for initializing power management.
 */
static void power_management_init(void)
{
    ret_code_t err_code;
    err_code = nrf_pwr_mgmt_init();
    APP_ERROR_CHECK(err_code);
}

/**@brief Function for handling the idle state (main loop).
 *
 * @details If there is no pending log operation, then sleep until next the next event occurs.
 */
void idle_state_handle(void)
{
    if (NRF_LOG_PROCESS() == false)
    {
        nrf_pwr_mgmt_run();
    }
}


void button_clicked_handler(nrf_drv_gpiote_pin_t pin, nrf_gpiote_polarity_t action)
{
    count++;
    if(count % 2 == 1){
      advertising_start();
    }
    else{
      advertising_stop();
    }
}
/**
 * @brief Function for configuring:  pin 20 for button input
 * and configures GPIOTE to give an interrupt on pin change.
 */
static void gpio_init(void)
{
    ret_code_t err_code;

    err_code = nrf_drv_gpiote_init();
    APP_ERROR_CHECK(err_code);

    nrf_drv_gpiote_in_config_t in_config = GPIOTE_CONFIG_IN_SENSE_LOTOHI(true);
    in_config.pull = NRF_GPIO_PIN_PULLUP;

    err_code = nrf_drv_gpiote_in_init(20, &in_config, button_clicked_handler);
    APP_ERROR_CHECK(err_code);

    nrf_drv_gpiote_in_event_enable(20, true);
}

/*
***************************************************************************************************
******************************** end of misc stuff ************************************************
*/




/**@brief Application main function.
 */
int main(void)
{



    APP_ERROR_CHECK(NRF_LOG_INIT(NULL));
    NRF_LOG_DEFAULT_BACKENDS_INIT();

    ble_stack_init();
    advertising_init();


    gpio_init();
    nrf_gpio_cfg_output(LED_FLASH_PIN); // flashing indicator led


    #ifdef  CONNECTABLE_BLE
    conn_params_init();
    #endif
    
    power_management_init();
    timers_init_start();
    advertising_start();

    twi_init();

    while(1)
    {
        idle_state_handle();
    }

}