use std::sync::Arc;
use co5_backflow_public::{CO5CTask,stage_descriptor::StageDescriptor, workunit::WorkUnit, errors::{workunit::CO5BuildWorkUnitError
        ,authorize::CO5AuthorizationError}, CO5CFreeMemory};
use uuid::Uuid;

#[link(name="clib", kind="static")]
#[allow(improper_ctypes)]
extern  "C"{
    fn get_callback(flag: u8) -> CO5CTask; 
    fn get_free_memory() -> CO5CFreeMemory;
}

#[repr(C)]
struct Dummy{
    from_rust: bool
}

unsafe impl Send for Dummy{}

#[no_mangle]
pub fn work_unit_factory(s: bool, i: &Uuid) -> Result<WorkUnit, CO5BuildWorkUnitError>{
    let ret = WorkUnit::new(s, i, 80, Box::new(Dummy{from_rust: false}));
    Ok(ret)
}

#[no_mangle]
pub fn authorize(_t: &str) -> Result<bool, CO5AuthorizationError>{
	return Ok(true);
}

#[no_mangle]
pub fn rest_workflow() -> Vec<StageDescriptor>{
    unsafe{
        let mut s = Vec::<StageDescriptor>::new();
        let s1: StageDescriptor = StageDescriptor { full_time_agents: 3, part_time_agents: 1, task: Some(stage_1), ctask: None, c_free_memory: None, agents_pause: 10, master_pause: 25, work_in_progress: 5, label: "STAGE_1".to_string(), logging: true};
        let s2: StageDescriptor = StageDescriptor { full_time_agents: 3, part_time_agents: 1, task: None, ctask: Some(get_callback(1)), c_free_memory: None, agents_pause: 10, master_pause: 25, work_in_progress: 5, label: "STAGE_2".to_string(), logging: true};
        let s3: StageDescriptor = StageDescriptor { full_time_agents: 3, part_time_agents: 1, task: None, ctask: Some(get_callback(0)), c_free_memory: Some(get_free_memory()), agents_pause: 10, master_pause: 25, work_in_progress: 5, label: "STAGE_3".to_string(), logging: true};
        let s4: StageDescriptor = StageDescriptor { full_time_agents: 3, part_time_agents: 1, task: Some(stage_3), ctask: None, c_free_memory: None, agents_pause: 10, master_pause: 25, work_in_progress: 5, label: "STAGE_4".to_string(), logging: true};
        s.push(s1);
        s.push(s2);
        s.push(s3);
        s.push(s4);
        return s;
    }
}

pub fn stage_1(mut wu: WorkUnit) -> WorkUnit{
    let d: &mut Dummy = wu.custom_struct.downcast_mut().expect("Not me");
    wu.payload = Some(Arc::new(vec![0;50]));
    wu.payload_capacity = 50;
    d.from_rust = true;
    return wu;
}

pub fn stage_3(mut wu: WorkUnit) -> WorkUnit{
    let d: &mut Dummy = wu.custom_struct.downcast_mut().expect("Not me");
    wu.done = d.from_rust;
    wu.http_status = 200;
    return wu;
}
