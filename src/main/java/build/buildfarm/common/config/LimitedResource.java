// Copyright 2023 The Buildfarm Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package build.buildfarm.common.config;

import build.buildfarm.common.Claim.Stage;
import lombok.Data;

/**
 * @class Limited Resource
 * @brief A fixed amount of a specific resource.
 * @details We define a limited resource as a counting semaphore whose configuration contains a name
 *     and a count representing a physical or logical group of units obtained by executors as a
 *     precondition to fulfill a long running operation. These units are released upon the
 *     operation's completion. The resource is requested by the action's platform properties.
 */
@Data
public class LimitedResource {
  public enum Type {
    // default, depletes until 0 and denies scheduling until released
    SEMAPHORE,

    // creates a pool of numeric ids, and can be injected into execution wrappers
    POOL,
  }

  private Type type = Type.SEMAPHORE;

  /**
   * @field name
   * @brief The name of the resource.
   * @details This should correspond to the platform property's key name:
   *     resources:<resoucename>:<amount>
   */
  private String name;

  /**
   * @field amount
   * @brief The total amount of the resource that's available for use during execution.
   * @details As a counting semaphore, this amount becomes the limit.
   */
  private int amount = 1;

  /**
   * @field stage
   * @brief The stage at which this resource should be released
   * @details The resource may be released when the specified stage is complete
   */
  private Stage releaseStage = Stage.EXECUTE_ACTION_STAGE;
}
