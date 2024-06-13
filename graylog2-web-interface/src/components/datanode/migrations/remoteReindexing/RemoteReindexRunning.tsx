/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
import * as React from 'react';
import { useState } from 'react';
import styled, { css } from 'styled-components';
import type { ColorVariant } from '@graylog/sawmill';

import { ConfirmDialog, ProgressBar } from 'components/common';
import { Alert, BootstrapModalWrapper, Button, Modal } from 'components/bootstrap';

import type { MigrationStepComponentProps } from '../../Types';
import MigrationStepTriggerButtonToolbar from '../common/MigrationStepTriggerButtonToolbar';
import type { MigrationStatus } from '../../hooks/useRemoteReindexMigrationStatus';
import useRemoteReindexMigrationStatus from '../../hooks/useRemoteReindexMigrationStatus';
import { MIGRATION_ACTIONS } from '../../Constants';

const IndicesContainer = styled.div`
  max-height: 100px;
  overflow-y: auto;
`;

const LogsContainer = styled.div`
  word-break: break-all;
  overflow-wrap: break-word;
  white-space: pre-wrap;
  max-height: 500px;

  & td {
    min-width: 64px;
    vertical-align: text-top;
    padding-bottom: 4px;
  }
`;

const StyledLog = styled.span<{ $colorVariant: ColorVariant }>(({ $colorVariant, theme }) => css`
  color: ${$colorVariant ? theme.colors.variant[$colorVariant] : 'inherit'};
`);

const getColorVariantFromLogLevel = (logLovel: string): ColorVariant|undefined => {
  switch (logLovel) {
    case 'ERROR':
      return 'danger';
    case 'WARNING':
      return 'warning';
    default:
      return undefined;
  }
};

const displayStatus = (status: MigrationStatus): string => {
  switch (status) {
    case 'NOT_STARTED':
      return 'LOADING...';
    case 'STARTING':
      return 'STARTING...';
    case 'RUNNING':
      return 'RUNNING...';
    default:
      return status || '';
  }
};

const RetryMigrateExistingData = 'RETRY_MIGRATE_EXISTING_DATA';

const RemoteReindexRunning = ({ currentStep, onTriggerStep }: MigrationStepComponentProps) => {
  const { nextSteps, migrationStatus, handleTriggerStep } = useRemoteReindexMigrationStatus(currentStep, onTriggerStep);
  const indicesWithErrors = migrationStatus?.indices.filter((index) => index.status === 'ERROR') || [];
  const [showLogView, setShowLogView] = useState<boolean>(false);
  const [showRetryMigrationConfirmDialog, setShowRetryMigrationConfirmDialog] = useState<boolean>(false);

  const hasMigrationFailed = migrationStatus?.progress === 100 && migrationStatus?.status === 'ERROR';

  return (
    <>
      We are currently migrating your existing data asynchronically (Graylog can be used while the reindexing is running),
      once the data migration is finished you will be automatically transitioned to the next step.
      <br />
      <br />
      <ProgressBar bars={[{
        animated: true,
        striped: true,
        value: migrationStatus?.progress || 0,
        bsStyle: 'info',
        label: `${displayStatus(migrationStatus?.status)} ${migrationStatus?.progress || 0}%`,
      }]} />
      {(indicesWithErrors.length > 0) && (
        <Alert title="Migration failed" bsStyle="danger">
          <IndicesContainer>
            {indicesWithErrors.map((index) => (
              <span key={index.name}>
                <b>{index.name}</b>
                <p>{index.error_msg}</p>
              </span>
            ))}
          </IndicesContainer>
        </Alert>
      )}
      <MigrationStepTriggerButtonToolbar nextSteps={(nextSteps || currentStep.next_steps).filter((step) => step !== RetryMigrateExistingData)} onTriggerStep={handleTriggerStep}>
        <Button bsStyle="default" bsSize="small" onClick={() => setShowLogView(true)}>Log View</Button>
        <Button bsStyle="default" bsSize="small" onClick={() => (hasMigrationFailed ? onTriggerStep(RetryMigrateExistingData) : setShowRetryMigrationConfirmDialog(true))}>{MIGRATION_ACTIONS[RetryMigrateExistingData]?.label}</Button>
      </MigrationStepTriggerButtonToolbar>
      {showRetryMigrationConfirmDialog && (
        <ConfirmDialog show={showRetryMigrationConfirmDialog}
                       title="Retry migrating existing data"
                       onCancel={() => setShowRetryMigrationConfirmDialog(false)}
                       onConfirm={() => {
                         onTriggerStep(RetryMigrateExistingData);
                         setShowRetryMigrationConfirmDialog(false);
                       }}>
          Are you sure you want to stop the current running remote reindexing migration and retry migrating existing data?
        </ConfirmDialog>
      )}
      {showLogView && (
        <BootstrapModalWrapper showModal={showLogView}
                               onHide={() => setShowLogView(false)}
                               bsSize="large"
                               backdrop>
          <Modal.Header closeButton>
            <Modal.Title>Remote Reindex Migration Logs</Modal.Title>
          </Modal.Header>
          <Modal.Body>
            <pre>
              {migrationStatus?.logs ? (
                <LogsContainer>
                  <table>
                    <tbody>
                      {migrationStatus.logs.map((log) => (
                        <tr title={new Date(log.timestamp).toLocaleString()}>
                          <td width={80}>[<StyledLog $colorVariant={getColorVariantFromLogLevel(log.log_level)}>{log.log_level}</StyledLog>]</td>
                          <td><StyledLog $colorVariant={getColorVariantFromLogLevel(log.log_level)}>{log.message}</StyledLog></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </LogsContainer>
              ) : ('No logs.')}
            </pre>
          </Modal.Body>
        </BootstrapModalWrapper>
      )}
    </>
  );
};

export default RemoteReindexRunning;
